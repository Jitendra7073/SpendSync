import Anthropic from '@anthropic-ai/sdk';
import { LlmError, type LlmChunk, type LlmProvider, type LlmRequest, type ToolCall } from './types';

function toAnthropicMessages(req: LlmRequest): Anthropic.MessageParam[] {
  const out: Anthropic.MessageParam[] = [];
  for (const m of req.messages) {
    if (m.role === 'user') out.push({ role: 'user', content: m.text });
    else if (m.role === 'assistant') {
      const blocks: Anthropic.ContentBlockParam[] = [];
      if (m.text) blocks.push({ type: 'text', text: m.text });
      for (const c of m.toolCalls) blocks.push({ type: 'tool_use', id: c.id, name: c.name, input: (c.args ?? {}) as Record<string, unknown> });
      if (blocks.length) out.push({ role: 'assistant', content: blocks });
    } else {
      out.push({
        role: 'user',
        content: m.results.map((r) => ({ type: 'tool_result' as const, tool_use_id: r.callId, content: r.content, is_error: r.isError })),
      });
    }
  }
  return out;
}

/** Optional, PAID last resort — only used when ASSISTANT_ALLOW_PAID=true and a key is set. */
export function anthropicProvider(opts: { model: string; label: string; client?: Anthropic }): LlmProvider {
  const client = opts.client ?? new Anthropic();
  return {
    id: `anthropic:${opts.model}`,
    provider: 'anthropic',
    label: opts.label,
    model: opts.model,
    paid: true,
    async *stream(req: LlmRequest): AsyncGenerator<LlmChunk> {
      try {
        const stream = client.messages.stream(
          {
            model: opts.model,
            max_tokens: req.maxTokens,
            system: [{ type: 'text', text: req.system, cache_control: { type: 'ephemeral' } }],
            tools: req.tools.map((t) => ({ name: t.name, description: t.description, input_schema: t.parameters as Anthropic.Tool['input_schema'] })),
            messages: toAnthropicMessages(req),
            ...(/^claude-(opus|sonnet|fable)-5/.test(opts.model) ? { output_config: { effort: 'low' as const } } : {}),
          },
          { signal: req.signal },
        );
        for await (const event of stream) {
          if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') yield { type: 'text', text: event.delta.text };
        }
        const message = await stream.finalMessage();
        const calls: ToolCall[] = message.content
          .filter((b): b is Anthropic.ToolUseBlock => b.type === 'tool_use')
          .map((b) => ({ id: b.id, name: b.name, args: b.input }));
        for (const call of calls) yield { type: 'tool_call', call };
        yield {
          type: 'end',
          reason: message.stop_reason === 'refusal' ? 'blocked' : calls.length ? 'tool_calls' : message.stop_reason === 'max_tokens' ? 'length' : 'stop',
          usage: { input: message.usage.input_tokens, output: message.usage.output_tokens },
        };
      } catch (err) {
        if (err instanceof LlmError) throw err;
        if (err instanceof Anthropic.RateLimitError) throw new LlmError('rate_limit', err.message);
        if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) throw new LlmError('auth', err.message);
        if (err instanceof Anthropic.NotFoundError) throw new LlmError('not_found', err.message);
        if (err instanceof Anthropic.APIConnectionError) throw new LlmError(req.signal?.aborted ? 'timeout' : 'network', err.message);
        if (err instanceof Anthropic.APIError && (err.status ?? 0) >= 500) throw new LlmError('server', err.message);
        throw new LlmError('bad_output', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
