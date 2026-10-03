import { NextResponse } from 'next/server';
import { withApi, corsPreflight } from '@/lib/api-handler';
import { runAssistant, type AssistantEvent } from '@/assistant/loop';
import { checkAssistantLimit } from '@/assistant/limiter';
import { chatRequestSchema, todayIn } from '@/assistant/request';
import { loadFeedbackHints } from '@/assistant/feedback';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

/**
 * POST /api/assistant/chat — streams the assistant's answer as Server-Sent Events
 * (`data: {json}\n\n`, see AssistantEvent). Read-only for now: answers questions and may offer to
 * open a screen; it cannot change data.
 */
export const POST = withApi(
  async (request, { userId }) => {
    const body = chatRequestSchema.parse(await request.json());
    checkAssistantLimit(userId);

    const encoder = new TextEncoder();
    const abort = new AbortController();
    request.signal.addEventListener('abort', () => abort.abort());

    const hints = await loadFeedbackHints(userId);

    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        const send = (event: AssistantEvent) => {
          try {
            controller.enqueue(encoder.encode(`data: ${JSON.stringify(event)}\n\n`));
          } catch {
            /* client went away */
          }
        };
        try {
          await runAssistant({
            userId,
            conversationId: body.conversationId,
            messages: body.messages,
            context: { today: todayIn(body.timezone), language: body.language, screen: body.screen, prefs: body.prefs, hints },
            emit: send,
            signal: abort.signal,
          });
        } finally {
          try {
            controller.close();
          } catch {
            /* already closed */
          }
        }
      },
      cancel() {
        abort.abort();
      },
    });

    return new NextResponse(stream, {
      headers: {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    });
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
