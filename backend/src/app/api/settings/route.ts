import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { settingsService } from '@/services/settings.service';
import { updateSettingsSchema } from '@/types/settings.types';

export const GET = withApi(
  async (_request, { userId }) => {
    const settings = await settingsService.getSettings(userId);
    return success(settings);
  },
  { auth: 'required' }
);

export const PATCH = withApi(
  async (request, { userId }) => {
    const data = updateSettingsSchema.parse(await request.json());
    const settings = await settingsService.updateSettings(userId, data);
    return success(settings);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
