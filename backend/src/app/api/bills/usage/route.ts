import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { billService } from '@/bills/service';


export const GET = withApi(
  async (_request, { userId }) => success(await billService.usage(userId)),
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
