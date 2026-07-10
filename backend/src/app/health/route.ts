import { NextResponse } from 'next/server';
import { sql } from 'drizzle-orm';
import { db } from '@/db/index';
import { config } from '@/config/env';

const MB = 1024 * 1024;

export async function GET() {
  const dbStart = Date.now();
  let dbConnected = false;
  try {
    await db.execute(sql`select 1`);
    dbConnected = true;
  } catch {
    dbConnected = false;
  }
  const dbLatencyMs = Date.now() - dbStart;

  const mem = process.memoryUsage();

  return NextResponse.json({
    status: dbConnected ? 'ok' : 'degraded',
    timestamp: new Date().toISOString(),
    environment: config.env,
    uptimeSeconds: Math.round(process.uptime()),
    db: {
      connected: dbConnected,
      latencyMs: dbLatencyMs,
    },
    memory: {
      rssMB: Math.round(mem.rss / MB),
      heapUsedMB: Math.round(mem.heapUsed / MB),
      heapTotalMB: Math.round(mem.heapTotal / MB),
    },
  });
}
