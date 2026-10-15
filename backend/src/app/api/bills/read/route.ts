import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billReadService } from '@/billai/service';
import { readImagesSchema } from '@/types/billai.types';

export const maxDuration = 60;

/** Pre-fill: reads the pages the phone just scanned. Nothing is stored or logged. */
export const POST = withApi(
  async (request, { userId }) => {
    const body = readImagesSchema.parse(await request.json());
    return success(await billReadService.readImages(userId, body.images, body.prefs));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
