import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { dashboardService } from '@/services/dashboard.service';

export const GET = withApi(
  async (request, { userId }) => {
    const month = request.nextUrl.searchParams.get('month') || new Date().toISOString().slice(0, 7);
    const summary = await dashboardService.getSummary(userId, month);
    return success(summary);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
