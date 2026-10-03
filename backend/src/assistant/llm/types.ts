/**
 * Provider-neutral shapes. Every model (Gemma, Llama, Mistral, Claude…) is adapted to these, so the
 * assistant loop, the tools and the failover router never care which one is answering.
 */
export type JsonSchema = Record<string, unknown>;

export interface ToolSpec {
  name: string;
  description: string;
  parameters: JsonSchema;
}

export interface ToolCall {
  id: string;
  name: string;
  args: unknown;
  /** Provider-specific data that must be sent back untouched on the next turn (e.g. Gemini thought signatures). */
  raw?: unknown;
}

export interface ToolOutcome {
  callId: string;
  name: string;
  content: string;
  isError: boolean;
}

export type LlmMessage =
  | { role: 'user'; text: string }
  | { role: 'assistant'; text: string; toolCalls: ToolCall[] }
  | { role: 'tool'; results: ToolOutcome[] };

export interface LlmRequest {
  system: string;
  messages: LlmMessage[];
  /** Empty = the model gets no tools (used in "context" mode for models without function calling). */
  tools: ToolSpec[];
  maxTokens: number;
  temperature?: number;
  signal?: AbortSignal;
}

export type LlmChunk =
  | { type: 'text'; text: string }
  | { type: 'tool_call'; call: ToolCall }
  | { type: 'end'; reason: 'stop' | 'tool_calls' | 'length' | 'blocked'; usage?: { input: number; output: number } };

export type LlmErrorKind =
  | 'rate_limit' // out of free quota / too many requests: back off for a while
  | 'auth' // bad or missing key: park for a long time
  | 'not_found' // model retired or renamed: park for a long time
  | 'no_tools' // this model/provider can't do function calling: retry in context mode
  | 'bad_output' // the model produced something unusable this time: try the next one
  | 'server' // 5xx
  | 'timeout' // no first token / stalled stream
  | 'network';

export class LlmError extends Error {
  constructor(
    public kind: LlmErrorKind,
    message: string,
    public retryAfterMs?: number,
  ) {
    super(message);
    this.name = 'LlmError';
  }
}

export interface LlmProvider {
  /** Stable id used in settings, e.g. "gemini:gemma-4-31b-it". */
  id: string;
  /** Short, human text shown to the user: "Gemma 4 31B · Google". */
  label: string;
  provider: string;
  model: string;
  /** True when it costs money per call (never part of the default free chain). */
  paid: boolean;
  stream(req: LlmRequest): AsyncGenerator<LlmChunk>;
}
