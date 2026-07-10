import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { settingsService } from '@/services/settings.service';
import { developerModeSchema } from '@/types/settings.types';

export const GET = withApi(
  async (_request, { userId }) => {
    const isDeveloperMode = await settingsService.isDeveloperMode(userId);
    return success({ developerMode: isDeveloperMode });
  },
  { auth: 'required' }
);

export const POST = withApi(
  async (request, { userId }) => {
    const { enabled } = developerModeSchema.parse(await request.json());
    const settings = enabled
      ? await settingsService.enableDeveloperMode(userId)
      : await settingsService.disableDeveloperMode(userId);
    return success({
      message: `Developer mode ${enabled ? 'enabled' : 'disabled'}`,
      settings,
    });
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
