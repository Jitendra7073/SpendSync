import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(__dirname, '..');
const FILTER: Record<string, string> = { transactions: 'liveTx', holds: 'liveHold', bills: 'liveBill' };

function tsFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return tsFiles(p);
    return p.endsWith('.ts') && !p.endsWith('.test.ts') ? [p] : [];
  });
}

/**
 * Every `.from(transactions|holds|bills)` must, inside its enclosing function (from the nearest `async ` before it
 * to the end of the statement), mention the live filter, `deletedAt`, or a `trash-aware` comment.
 * ponytail: text heuristic, not a parser — one function with two queries passes if either has it.
 */
function unfiltered(src: string): number[] {
  const lines: number[] = [];
  for (const m of src.matchAll(/\.from\((transactions|holds|bills)\)/g)) {
    const at = m.index ?? 0;
    const start = Math.max(0, src.lastIndexOf('async ', at));
    const end = src.indexOf(';', at);
    const window = src.slice(start, end === -1 ? undefined : end);
    if (!window.includes(FILTER[m[1]]) && !window.includes('deletedAt') && !window.includes('trash-aware')) {
      lines.push(src.slice(0, at).split('\n').length);
    }
  }
  return lines;
}

describe('Trash guard', () => {
  it('catches an unfiltered read of each table', () => {
    for (const t of ['transactions', 'holds', 'bills']) {
      expect(unfiltered(`async function f() { return db.select().from(${t}).where(eq(${t}.userId, u)); }`)).toEqual([1]);
    }
    expect(unfiltered('async function f() { return db.select().from(bills).where(and(mine, liveBill)); }')).toEqual([]);
  });

  it('every query on transactions/holds/bills filters deleted rows', () => {
    const misses = tsFiles(SRC).flatMap((file) =>
      unfiltered(readFileSync(file, 'utf8')).map((line) => `${relative(SRC, file)}:${line}`),
    );
    expect(misses).toEqual([]);
  });
});
