import { BadRequestError, ConflictError, NotFoundError } from '../utils/errors';

/** Keyset position in the Trash list: everything strictly older than (at, id). */
export interface TrashCursor {
  at: string;
  id: string;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function encodeCursor(c: TrashCursor): string {
  return Buffer.from(JSON.stringify(c)).toString('base64url');
}

/** The cursor comes from the client, so it is validated like any other input. */
export function decodeCursor(s: string | undefined): TrashCursor | null {
  if (!s) return null;
  try {
    const c = JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));
    if (typeof c?.at === 'string' && typeof c?.id === 'string' && UUID.test(c.id) && !Number.isNaN(Date.parse(c.at))) {
      return { at: new Date(c.at).toISOString(), id: c.id.toLowerCase() };
    }
  } catch {
    // fall through
  }
  throw new BadRequestError('Invalid cursor', 'BAD_CURSOR');
}

/** Newest first, ties on id descending — must match `ORDER BY deleted_at DESC, id DESC`. */
function newerFirst(x: { id: string; deletedAt: Date }, y: { id: string; deletedAt: Date }) {
  return y.deletedAt.getTime() - x.deletedAt.getTime() || (y.id < x.id ? -1 : y.id > x.id ? 1 : 0);
}

/** Each input holds up to limit+1 rows of one kind, already newest first. */
export function mergePage<T extends { id: string; deletedAt: Date }>(a: T[], b: T[], limit: number) {
  const all = [...a, ...b].sort(newerFirst);
  const items = all.slice(0, limit);
  const last = items[items.length - 1];
  const nextCursor = all.length > limit && last ? encodeCursor({ at: last.deletedAt.toISOString(), id: last.id }) : null;
  return { items, nextCursor };
}

/** Delete-forever works only on rows already in the Trash: one stolen call can't erase live data. */
export function assertInTrash(row: { deletedAt: Date | null } | undefined): void {
  if (!row) throw new NotFoundError('Not found');
  if (!row.deletedAt) throw new ConflictError('Move it to the Trash first', 'NOT_IN_TRASH');
}

export function assertParentLive(parentDeletedAt: Date | null): void {
  if (parentDeletedAt) throw new ConflictError('Restore its transaction instead', 'PARENT_DELETED');
}
