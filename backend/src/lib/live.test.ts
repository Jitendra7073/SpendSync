import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(__dirname, '..');

function tsFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return tsFiles(p);
    return p.endsWith('.ts') && !p.endsWith('.test.ts') ? [p] : [];
  });
}

/**
 * Every read of transactions/holds must skip the Trash. For each `.from(transactions|holds)` the
 * enclosing function (from the nearest `async ` before it to the end of the statement) must mention
 * the live filter, `deletedAt`, or a `trash-aware` comment (Trash code that reads deleted rows on purpose).
 * ponytail: text heuristic, not a parser — one function with two queries passes if either has it.
 */
describe('Trash guard', () => {
  it('every query on transactions/holds filters deleted rows', () => {
    const misses: string[] = [];
    for (const file of tsFiles(SRC)) {
      const src = readFileSync(file, 'utf8');
      for (const m of src.matchAll(/\.from\((transactions|holds)\)/g)) {
        const at = m.index ?? 0;
        const start = Math.max(0, src.lastIndexOf('async ', at));
        const end = src.indexOf(';', at);
        const window = src.slice(start, end === -1 ? undefined : end);
        const filter = m[1] === 'transactions' ? 'liveTx' : 'liveHold';
        if (!window.includes(filter) && !window.includes('deletedAt') && !window.includes('trash-aware')) {
          const line = src.slice(0, at).split('\n').length;
          misses.push(`${relative(SRC, file)}:${line} .from(${m[1]})`);
        }
      }
    }
    expect(misses).toEqual([]);
  });
});
