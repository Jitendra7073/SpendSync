import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { sharedRouter } from '@/assistant/loop';
import { BLOCKED_ACTIONS } from '@/assistant/permissions';
import { toolCatalog } from '@/assistant/tools';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/** GET /api/assistant/status — what the Settings → Assistant screen shows: tools, locked actions, models and their health. */
export const GET = withApi(
  async () => {
    return success({
      tools: toolCatalog(),
      blocked: BLOCKED_ACTIONS,
      models: sharedRouter()
        .health()
        .map((m) => ({ id: m.id, label: m.label, paid: m.paid, healthy: m.healthy, toolsMode: m.mode === 'tools' })),
    });
  },
  { auth: 'required' },
);

export { corsPreflight as OPTIONS };
