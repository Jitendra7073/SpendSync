import { LlmError, type LlmChunk, type LlmProvider, type LlmRequest } from './types';

/** "tools" = the model calls functions itself. "context" = no function calling; data is pre-fetched into the prompt. */
export type Mode = 'tools' | 'context';

export type RouterEvent =
  | { type: 'source'; provider: LlmProvider }
  | { type: 'chunk'; chunk: LlmChunk }
  /** The provider died after it had already started writing; the text so far must be thrown away. */
  | { type: 'reset' };

export class AllProvidersFailed extends Error {
  constructor(public reasons: string[]) {
    super(`All assistant models failed: ${reasons.join(' | ')}`);
    this.name = 'AllProvidersFailed';
  }
}

export interface ProviderHealth {
  id: string;
  label: string;
  provider: string;
  model: string;
  paid: boolean;
  /** Not cooling down right now. */
  healthy: boolean;
  mode: Mode;
  retryInMs: number;
  lastError?: string;
}

interface State {
  cooldownUntil: number;
  failures: number;
  mode: Mode;
  modeSince: number;
  lastError?: string;
}

export interface RouterOptions {
  /** Max wait for the first word before giving up on a model and moving on. */
  firstTokenMs?: number;
  /** Max silence in the middle of an answer. */
  idleMs?: number;
  now?: () => number;
}

const MIN = 60_000;

/**
 * Keeps the assistant answering no matter which free model is out of quota, slow or broken.
 *
 * For every request it walks the chain in order (the user's preferred model first), skipping models
 * that are cooling down. A model that fails before its first word is silently replaced by the next
 * one; one that fails mid-answer triggers a `reset` and the next model answers from the start. A model
 * without function calling is retried in "context" mode instead of being dropped. Cool-downs grow
 * with repeated failures and clear on the first success. Only when every model has failed does it
 * give up — and the caller then uses its built-in offline answer rather than showing an error.
 */
export class ProviderRouter {
  private state = new Map<string, State>();
  private firstTokenMs: number;
  private idleMs: number;
  private now: () => number;

  constructor(private providers: LlmProvider[], opts: RouterOptions = {}) {
    this.firstTokenMs = opts.firstTokenMs ?? 6_000;
    this.idleMs = opts.idleMs ?? 20_000;
    this.now = opts.now ?? Date.now;
    for (const p of providers) this.state.set(p.id, { cooldownUntil: 0, failures: 0, mode: 'tools', modeSince: 0 });
  }

  get size(): number {
    return this.providers.length;
  }

  health(): ProviderHealth[] {
    const t = this.now();
    return this.providers.map((p) => {
      const s = this.state.get(p.id)!;
      return {
        id: p.id,
        label: p.label,
        provider: p.provider,
        model: p.model,
        paid: p.paid,
        healthy: s.cooldownUntil <= t,
        mode: this.effectiveMode(s),
        retryInMs: Math.max(0, s.cooldownUntil - t),
        lastError: s.lastError,
      };
    });
  }

  /** Ready models first (the user's choice leading), then cooling ones, soonest-to-recover first. */
  private order(preferredId?: string): LlmProvider[] {
    const t = this.now();
    const sorted = [...this.providers];
    if (preferredId) {
      const i = sorted.findIndex((p) => p.id === preferredId);
      if (i > 0) sorted.unshift(...sorted.splice(i, 1));
    }
    const ready = sorted.filter((p) => this.state.get(p.id)!.cooldownUntil <= t);
    const cooling = sorted
      .filter((p) => this.state.get(p.id)!.cooldownUntil > t)
      .sort((a, b) => this.state.get(a.id)!.cooldownUntil - this.state.get(b.id)!.cooldownUntil);
    return [...ready, ...cooling];
  }

  private effectiveMode(s: State): Mode {
    // Function calling is re-tested every 15 minutes in case the model got fixed.
    if (s.mode === 'context' && this.now() - s.modeSince > 15 * MIN) s.mode = 'tools';
    return s.mode;
  }

  private markOk(p: LlmProvider): void {
    const s = this.state.get(p.id)!;
    s.failures = 0;
    s.cooldownUntil = 0;
    s.lastError = undefined;
  }

  private markFailed(p: LlmProvider, e: LlmError): void {
    const s = this.state.get(p.id)!;
    s.failures += 1;
    s.lastError = `${e.kind}: ${e.message}`.slice(0, 200);
    const grow = (base: number, cap: number) => Math.min(base * 2 ** Math.min(s.failures - 1, 6), cap);
    let wait: number;
    switch (e.kind) {
      case 'rate_limit':
        // Honour the provider's own hint, but never retry sooner than 20s; repeated limits (a spent
        // daily quota) back off up to 30 minutes.
        wait = Math.max(e.retryAfterMs ?? 0, grow(60_000, 30 * MIN), 20_000);
        break;
      case 'auth':
      case 'not_found':
        wait = 6 * 60 * MIN; // a wrong key or retired model won't fix itself within hours
        break;
      case 'bad_output':
        wait = 5_000;
        break;
      default:
        wait = grow(20_000, 5 * MIN); // server / timeout / network
    }
    s.cooldownUntil = this.now() + wait;
  }

  /**
   * Streams one model turn from the best available model.
   * `build` is called per attempt so the request can differ by model and mode.
   */
  async *run(
    build: (provider: LlmProvider, mode: Mode) => Promise<LlmRequest> | LlmRequest,
    opts: { preferredId?: string; signal?: AbortSignal } = {},
  ): AsyncGenerator<RouterEvent> {
    const reasons: string[] = [];

    for (const provider of this.order(opts.preferredId)) {
      const s = this.state.get(provider.id)!;
      let mode = this.effectiveMode(s);

      for (let tryNo = 0; tryNo < 2; tryNo++) {
        let emitted = false;
        const controller = new AbortController();
        const onOuterAbort = () => controller.abort();
        opts.signal?.addEventListener('abort', onOuterAbort);
        try {
          const request = await build(provider, mode);
          const it = provider.stream({ ...request, signal: controller.signal });
          let first = true;
          try {
            while (true) {
              const r = await this.nextWithin(it, first ? this.firstTokenMs : this.idleMs, controller);
              if (r.done) break;
              if (first) {
                first = false;
                yield { type: 'source', provider };
              }
              emitted = true;
              yield { type: 'chunk', chunk: r.value };
              if (r.value.type === 'end') {
                this.markOk(provider);
                return;
              }
            }
          } finally {
            void it.return?.(undefined);
          }
          // Stream closed without an explicit end: fine if it produced something, otherwise a failure.
          if (emitted) {
            this.markOk(provider);
            return;
          }
          throw new LlmError('bad_output', 'the model returned nothing');
        } catch (err) {
          if (opts.signal?.aborted) throw err;
          const e = err instanceof LlmError ? err : new LlmError('server', err instanceof Error ? err.message : String(err));
          if (emitted) yield { type: 'reset' };
          if (e.kind === 'no_tools' && mode === 'tools') {
            // This model can't call functions — keep it, but feed it the data directly instead.
            s.mode = 'context';
            s.modeSince = this.now();
            mode = 'context';
            continue;
          }
          reasons.push(`${provider.id} → ${e.kind}`);
          this.markFailed(provider, e.kind === 'no_tools' ? new LlmError('bad_output', e.message) : e);
          break;
        } finally {
          opts.signal?.removeEventListener('abort', onOuterAbort);
          controller.abort();
        }
      }
    }

    throw new AllProvidersFailed(reasons);
  }

  /** `it.next()` with a deadline; on timeout the underlying request is aborted. */
  private nextWithin<T>(it: AsyncGenerator<T>, ms: number, controller: AbortController): Promise<IteratorResult<T>> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        controller.abort();
        reject(new LlmError('timeout', `no data for ${ms}ms`));
      }, ms);
      it.next().then(
        (v) => {
          clearTimeout(timer);
          resolve(v);
        },
        (e) => {
          clearTimeout(timer);
          reject(e);
        },
      );
    });
  }
}
