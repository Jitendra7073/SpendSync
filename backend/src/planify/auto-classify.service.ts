import { db } from '../db';
import { budgets, planMatches } from '../db/schema/budgets.schema';
import { transactions } from '../db/schema/transactions.schema';
import { eq, and, gt, desc } from 'drizzle-orm';
import { askModel } from '../assistant/llm/ask';

export class AutoClassifyService {
  /**
   * Processes uncategorized transactions for a user using an LLM.
   */
  async classifyForUser(userId: string) {
    const today = new Date();
    const month = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;

    // Get current month's buckets
    const userBuckets = await db
      .select()
      .from(budgets)
      .where(and(eq(budgets.userId, userId), eq(budgets.month, month)));

    if (userBuckets.length === 0) return { success: true, count: 0 };

    // Get recent transactions (last 30 days)
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const recentTxs = await db
      .select()
      .from(transactions)
      .where(
        and(
          eq(transactions.userId, userId),
          eq(transactions.type, 'debit'),
          gt(transactions.createdAt, thirtyDaysAgo)
        )
      )
      .orderBy(desc(transactions.createdAt))
      .limit(100); // Limit to 100 for token efficiency

    // Get existing matches
    const existingMatches = await db
      .select()
      .from(planMatches)
      .where(eq(planMatches.userId, userId));

    const matchedKeys = new Set(existingMatches.map(m => `${m.kind}:${m.key.toLowerCase()}`));
    const bucketCategories = new Set(userBuckets.map(b => b.category.toLowerCase()));

    // Filter to uncategorized transactions
    const uncategorized = recentTxs.filter(tx => {
      const cat = tx.category.toLowerCase();
      const merchant = tx.merchant.toLowerCase();
      // If it exactly matches a bucket category, it's already categorized
      if (bucketCategories.has(cat)) return false;
      // If there's an existing match rule for this category or merchant, skip
      if (matchedKeys.has(`category:${cat}`)) return false;
      if (matchedKeys.has(`merchant:${merchant}`)) return false;
      return true;
    });

    if (uncategorized.length === 0) return { success: true, count: 0 };

    // Group unique merchants/categories to ask LLM about
    const toClassify = new Map<string, { kind: 'merchant' | 'category', key: string, label: string }>();
    
    for (const tx of uncategorized) {
      if (tx.merchant) {
        const key = tx.merchant.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();
        if (key && !toClassify.has(`merchant:${key}`)) {
          toClassify.set(`merchant:${key}`, { kind: 'merchant', key, label: tx.merchant });
        }
      } else {
        const key = tx.category.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();
        if (key && !toClassify.has(`category:${key}`)) {
          toClassify.set(`category:${key}`, { kind: 'category', key, label: tx.category });
        }
      }
    }

    const items = Array.from(toClassify.values());
    if (items.length === 0) return { success: true, count: 0 };

    // Prepare LLM prompt
    const bucketList = userBuckets.map(b => `- ${b.category} (Name: ${b.name})`).join('\n');
    const itemList = items.map((item, i) => `[${i}] Kind: ${item.kind}, Name: "${item.label}"`).join('\n');

    const prompt = `
You are an expert financial categorizer.
The user has the following budget buckets:
${bucketList}

We have the following uncategorized merchants or transaction categories:
${itemList}

Your task is to classify each item into one of the user's budget buckets IF AND ONLY IF it is a highly confident semantic match.
If an item does not clearly belong to any bucket, classify it as "NONE".

Return ONLY a JSON array of objects with the format:
[
  { "index": 0, "bucket": "BucketCategoryHere" },
  { "index": 1, "bucket": "NONE" }
]
No markdown, no explanations, just the JSON array.
    `.trim();

    try {
      const response = await askModel(
        "You are an expert financial categorizer.",
        prompt,
        500,
        0.1
      );

      const text = response.text;
      if (!text) return { success: false, error: 'No response from LLM' };

      const jsonStr = text.substring(text.indexOf('['), text.lastIndexOf(']') + 1);
      const results = JSON.parse(jsonStr) as { index: number, bucket: string }[];

      let insertedCount = 0;

      for (const res of results) {
        if (res.bucket === 'NONE') continue;
        
        // Verify the bucket exists
        if (!bucketCategories.has(res.bucket.toLowerCase())) continue;

        const item = items[res.index];
        if (!item) continue;

        // Insert as ai_auto match
        await db.insert(planMatches).values({
          userId,
          bucket: res.bucket,
          kind: item.kind,
          key: item.key,
          label: item.label,
          verdict: 'ai_auto',
        }).onConflictDoNothing(); // Prevent duplicates

        insertedCount++;
      }

      return { success: true, count: insertedCount };
    } catch (e) {
      console.error('Auto-classify error:', e);
      return { success: false, error: String(e) };
    }
  }
}

export const autoClassifyService = new AutoClassifyService();
