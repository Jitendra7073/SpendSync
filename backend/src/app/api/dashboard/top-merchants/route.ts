import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { dashboardService } from '@/services/dashboard.service';

export const GET = withApi(
  async (request, { userId }) => {
    const limit = parseInt(request.nextUrl.searchParams.get('limit') || '') || 10;
    const merchants = await dashboardService.getTopMerchants(userId, limit);
    return success(merchants);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
