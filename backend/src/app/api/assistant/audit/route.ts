import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { recentToolCalls } from '@/assistant/audit';

/** GET /api/assistant/audit — the user's own "what the assistant did" log (newest first). */
export const GET = withApi(
  async (request, { userId }) => {
    const limit = Number(request.nextUrl.searchParams.get('limit') ?? 50);
    const rows = await recentToolCalls(userId, Number.isFinite(limit) ? limit : 50);
    return success(
      rows.map((r) => ({
        id: r.id,
        tool: r.toolName,
        tier: r.tier,
        ok: r.ok,
        durationMs: r.durationMs,
        at: r.createdAt,
      })),
    );
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
