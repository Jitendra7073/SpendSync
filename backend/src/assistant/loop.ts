import { logToolCall } from './audit';
import { FollowupFilter } from './followups';
import { ToolTagFilter } from './tagfilter';
import { offlineAnswer, prefetchData } from './fallback';
import { buildProviders } from './llm/chain';
import { AllProvidersFailed, ProviderRouter, type Mode } from './llm/router';
import type { LlmMessage, LlmProvider, LlmRequest, ToolCall, ToolOutcome } from './llm/types';
import { canRun } from './permissions';
import { contextBlock, SYSTEM_PROMPT, type RequestContext } from './prompt';
import { findTool, toolSpecs, type ToolContext, type UiAction } from './tools';

/** Everything the phone can receive over the SSE stream. */
export type AssistantEvent =
  | { type: 'delta'; text: string }
  | { type: 'tool'; name: string; status: 'running' | 'done' | 'failed' }
  | { type: 'ui_action'; action: UiAction }
  | { type: 'followups'; items: string[] }
  /** Which model is answering (re-sent if a backup model takes over). `id` is "offline" for the built-in answer. */
  | { type: 'source'; id: string; label: string }
  /** The model that was answering died mid-way: the phone must discard the text of this reply so far. */
  | { type: 'reset' }
  | { type: 'done'; usage: { input: number; output: number; cacheRead: number } }
  | { type: 'error'; code: 'not_configured' | 'busy' | 'unavailable' | 'refused' | 'too_long' };

export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

export interface RunOptions {
  userId: string;
  conversationId: string;
  messages: ChatTurn[];
  context: RequestContext;
  emit: (event: AssistantEvent) => void;
  signal?: AbortSignal;
  /** Injected in tests; production uses the shared router (so cool-downs persist between requests). */
  router?: ProviderRouter;
}

const MAX_TOOL_ROUNDS = 5;
/** Keep tool output small: it is sent back as input on every following round. */
const MAX_TOOL_RESULT_CHARS = 6000;

let shared: ProviderRouter | null = null;
/** One router per server instance: it remembers which free models are out of quota. */
export function sharedRouter(): ProviderRouter {
  return (shared ??= new ProviderRouter(buildProviders()));
}

function truncate(json: string): string {
  return json.length <= MAX_TOOL_RESULT_CHARS ? json : `${json.slice(0, MAX_TOOL_RESULT_CHARS)}…[truncated]`;
}

export async function runAssistant(opts: RunOptions): Promise<void> {
  const { userId, conversationId, context, emit, signal } = opts;
  const router = opts.router ?? sharedRouter();
  const prefs = context.prefs;
  const disabled = new Set(prefs?.disabledTools ?? []);
  const toolCtx: ToolContext = { userId, today: context.today };
  const question = opts.messages.at(-1)?.content ?? '';

  // The per-request context goes on the LAST user turn so the system prompt + tools stay cacheable.
  const base: LlmMessage[] = opts.messages.map((m, i) =>
    i === opts.messages.length - 1 && m.role === 'user'
      ? { role: 'user', text: `${m.content}\n\n${contextBlock(context, [...disabled])}` }
      : m.role === 'user'
        ? { role: 'user', text: m.content }
        : { role: 'assistant', text: m.content, toolCalls: [] },
  );

  const tools = toolSpecs(disabled);
  const usage = { input: 0, output: 0, cacheRead: 0 };
  let filter = new FollowupFilter();
  let tags = new ToolTagFilter();
  const offered = new Set<string>();
  /** Text -> tag filter (drops fake tool tags, turns open_screen into a button) -> follow-up filter. */
  const showText = (raw: string): string => {
    const t = tags.push(raw);
    for (const screen of t.screens) {
      if (offered.has(screen)) continue;
      offered.add(screen);
      emit({ type: 'ui_action', action: { type: 'open_screen', screen } });
    }
    return filter.push(t.text);
  };
  let spoke = false;
  let lastSource = '';

  // For models without function calling: look the facts up first and put them in the prompt.
  let prefetched: Promise<string> | undefined;
  const contextMessages = async (): Promise<LlmMessage[]> => {
    prefetched ??= prefetchData(toolCtx, question, disabled);
    const data = await prefetched;
    const out = [...base];
    const last = out[out.length - 1];
    if (last?.role === 'user') out[out.length - 1] = { role: 'user', text: `${last.text}\n\n${data}` };
    return out;
  };

  const finishOffline = async () => {
    if (spoke) emit({ type: 'reset' });
    const answer = await offlineAnswer(toolCtx, question, context.language, disabled);
    emit({ type: 'source', id: 'offline', label: 'offline' });
    emit({ type: 'delta', text: answer.text });
    for (const screen of answer.actions.slice(0, 1)) emit({ type: 'ui_action', action: { type: 'open_screen', screen } });
    emit({ type: 'followups', items: answer.followups });
    emit({ type: 'done', usage });
  };

  const history: LlmMessage[] = [...base];

  try {
    if (router.size === 0) return await finishOffline();

    for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
      const calls: ToolCall[] = [];
      let roundText = '';
      let ended: 'stop' | 'tool_calls' | 'length' | 'blocked' = 'stop';

      const build = async (_p: LlmProvider, mode: Mode): Promise<LlmRequest> => ({
        system: SYSTEM_PROMPT,
        messages: mode === 'context' ? await contextMessages() : history,
        tools: mode === 'context' ? [] : tools,
        maxTokens: 2048,
      });

      for await (const ev of router.run(build, { preferredId: prefs && prefs.model !== 'auto' ? prefs.model : undefined, signal })) {
        if (ev.type === 'source') {
          if (ev.provider.id !== lastSource) {
            lastSource = ev.provider.id;
            emit({ type: 'source', id: ev.provider.id, label: ev.provider.label });
          }
        } else if (ev.type === 'reset') {
          if (spoke) emit({ type: 'reset' });
          spoke = false;
          roundText = '';
          calls.length = 0;
          filter = new FollowupFilter();
          tags = new ToolTagFilter();
        } else if (ev.chunk.type === 'text') {
          roundText += ev.chunk.text;
          const visible = showText(ev.chunk.text);
          if (visible) {
            spoke = true;
            emit({ type: 'delta', text: visible });
          }
        } else if (ev.chunk.type === 'tool_call') {
          calls.push(ev.chunk.call);
        } else {
          ended = ev.chunk.reason;
          usage.input += ev.chunk.usage?.input ?? 0;
          usage.output += ev.chunk.usage?.output ?? 0;
        }
      }

      if (ended === 'blocked') {
        emit({ type: 'error', code: 'refused' });
        return;
      }
      // Never run tools on a possibly-truncated tool call; show what we have.
      if (ended === 'length' || calls.length === 0) break;

      history.push({ role: 'assistant', text: roundText, toolCalls: calls });

      // Run this round's tools in parallel; all results go back in ONE message.
      const results = await Promise.all(
        calls.map(async (use): Promise<ToolOutcome> => {
          const started = Date.now();
          const def = findTool(use.name);
          const fail = (error: string): ToolOutcome => {
            emit({ type: 'tool', name: use.name, status: 'failed' });
            if (def) logToolCall({ userId, conversationId, toolName: use.name, tier: def.tier, ok: false, durationMs: Date.now() - started, error });
            return { callId: use.id, name: use.name, content: error, isError: true };
          };

          if (!def) return fail('Unknown tool.');
          if (!canRun(def.tier)) return fail('This action is not available to the assistant.');
          if (disabled.has(def.name)) return fail('The user turned this tool off in Settings → Assistant.');
          const parsed = def.input.safeParse(use.args);
          if (!parsed.success) return fail('Invalid arguments. Check the tool description and try again.');

          emit({ type: 'tool', name: use.name, status: 'running' });
          try {
            const out = await def.run(toolCtx, parsed.data);
            if (out.uiAction) emit({ type: 'ui_action', action: out.uiAction });
            emit({ type: 'tool', name: use.name, status: 'done' });
            logToolCall({ userId, conversationId, toolName: use.name, tier: def.tier, ok: true, durationMs: Date.now() - started });
            return { callId: use.id, name: use.name, content: truncate(JSON.stringify(out.data)), isError: false };
          } catch (err) {
            return fail(err instanceof Error ? err.message : 'Tool failed.');
          }
        }),
      );
      history.push({ role: 'tool', results });
    }

    const tail = showText(tags.finish().text);
    if (tail) emit({ type: 'delta', text: tail });
    const rest = filter.finish();
    if (rest.text) emit({ type: 'delta', text: rest.text });
    if (rest.followups.length) emit({ type: 'followups', items: rest.followups });
    emit({ type: 'done', usage });
  } catch (err) {
    if (signal?.aborted) return;
    // Every model failed (or something unexpected broke): never show an error — answer from the built-in guide and data.
    try {
      await finishOffline();
    } catch {
      emit({ type: 'error', code: err instanceof AllProvidersFailed ? 'busy' : 'unavailable' });
    }
  }
}
