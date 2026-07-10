import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { dashboardService } from '@/services/dashboard.service';

export const GET = withApi(
  async (request, { userId }) => {
    const months = parseInt(request.nextUrl.searchParams.get('months') || '') || 6;
    const trend = await dashboardService.getMonthlyTrend(userId, months);
    return success(trend);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
