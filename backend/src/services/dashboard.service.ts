import { eq, and, sql, gte, lte } from 'drizzle-orm';
import { db } from '../db/index.js';
import { transactions, budgets } from '../db/schema/index.js';

/**
 * Dashboard Service
 * Handles analytics and aggregated data
 */
export class DashboardService {
  /**
   * Get dashboard summary for a specific month
   */
  async getSummary(userId: string, month: string) {
    const startDate = new Date(`${month}-01`);
    const endDate = new Date(startDate.getFullYear(), startDate.getMonth() + 1, 0);

    // Get category-wise spending
    const categorySpending = await db
      .select({
        category: transactions.category,
        totalDebit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        totalCredit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        count: sql<number>`COUNT(*)`,
      })
      .from(transactions)
      .where(
        and(
          eq(transactions.userId, userId),
          gte(transactions.createdAt, startDate),
          lte(transactions.createdAt, endDate)
        )
      )
      .groupBy(transactions.category);

    // Get budgets for the month
    const monthBudgets = await db
      .select()
      .from(budgets)
      .where(and(eq(budgets.userId, userId), eq(budgets.month, month)));

    // Combine spending with budgets
    const categoryBreakdown = categorySpending.map((spending) => {
      const budget = monthBudgets.find((b) => b.category === spending.category);
      const totalDebit = parseFloat(spending.totalDebit);
      const limitAmount = budget ? parseFloat(budget.limitAmount) : null;

      return {
        category: spending.category,
        spent: totalDebit,
        earned: parseFloat(spending.totalCredit),
        transactionCount: spending.count,
        budget: limitAmount,
        remaining: limitAmount ? limitAmount - totalDebit : null,
        percentageUsed: limitAmount ? (totalDebit / limitAmount) * 100 : null,
      };
    });

    // Calculate totals
    const totals = {
      totalSpent: categoryBreakdown.reduce((sum, cat) => sum + cat.spent, 0),
      totalEarned: categoryBreakdown.reduce((sum, cat) => sum + cat.earned, 0),
      totalTransactions: categoryBreakdown.reduce((sum, cat) => sum + cat.transactionCount, 0),
      totalBudget: monthBudgets.reduce((sum, b) => sum + parseFloat(b.limitAmount), 0),
      netAmount: 0,
    };

    totals.netAmount = totals.totalEarned - totals.totalSpent;

    // Get monthly trend (last 6 months)
    const monthlyTrend = await this.getMonthlyTrend(userId, 6);

    return {
      month,
      totals,
      categoryBreakdown,
      monthlyTrend,
    };
  }

  /**
   * Get monthly spending trend
   */
  async getMonthlyTrend(userId: string, months: number = 6) {
    const result = await db
      .select({
        month: sql<string>`TO_CHAR(${transactions.createdAt}, 'YYYY-MM')`,
        totalDebit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        totalCredit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        count: sql<number>`COUNT(*)`,
      })
      .from(transactions)
      .where(
        and(
          eq(transactions.userId, userId),
          gte(transactions.createdAt, sql`NOW() - INTERVAL '${months} months'`)
        )
      )
      .groupBy(sql`TO_CHAR(${transactions.createdAt}, 'YYYY-MM')`)
      .orderBy(sql`TO_CHAR(${transactions.createdAt}, 'YYYY-MM')`);

    return result.map((row) => ({
      month: row.month,
      spent: parseFloat(row.totalDebit),
      earned: parseFloat(row.totalCredit),
      net: parseFloat(row.totalCredit) - parseFloat(row.totalDebit),
      transactionCount: row.count,
    }));
  }

  /**
   * Get top merchants by spending
   */
  async getTopMerchants(userId: string, limit: number = 10) {
    return await db
      .select({
        merchant: transactions.merchant,
        totalSpent: sql<string>`SUM(${transactions.amount}::numeric)`,
        count: sql<number>`COUNT(*)`,
      })
      .from(transactions)
      .where(and(eq(transactions.userId, userId), eq(transactions.type, 'debit')))
      .groupBy(transactions.merchant)
      .orderBy(sql`SUM(${transactions.amount}::numeric) DESC`)
      .limit(limit);
  }
}

export const dashboardService = new DashboardService();
