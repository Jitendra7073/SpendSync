import { withApi, corsPreflight } from '@/lib/api-handler';
import { success } from '@/lib/response';
import { sendBillsEmail } from '@/bills/billmail.service';
import { sendBillsSchema } from '@/types/billmail.types';

export const maxDuration = 60;

/** The user tapped Send on the email card. Always to their own account address. */
export const POST = withApi(
  async (request, { userId }) => {
    const { transactionIds } = sendBillsSchema.parse(await request.json());
    return success(await sendBillsEmail(userId, [...new Set(transactionIds)]));
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
