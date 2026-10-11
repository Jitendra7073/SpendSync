import { describe, expect, it } from 'vitest';
import { pagePlan, pagesKey, readImageUrl, sniffMime } from './pages';

const b = (id: string, format = 'jpg', pages: number | null = null, version = 1) => ({ id, publicId: `p/${id}`, version, format, pages });

describe('pages', () => {
  it('key is stable for the same pages and changes when one changes', () => {
    expect(pagesKey([b('a'), b('b')])).toBe(pagesKey([b('b'), b('a')]));
    expect(pagesKey([b('a'), b('b')])).not.toBe(pagesKey([b('a'), b('b', 'jpg', null, 2)]));
    expect(pagesKey([b('a')])).not.toBe(pagesKey([b('a'), b('b')]));
  });
  it('sends at most 5 page images, PDFs page by page', () => {
    const plan = pagePlan([b('img'), b('pdf', 'pdf', 9)]);
    expect(plan).toHaveLength(5);
    expect(plan[0]).toEqual({ publicId: 'p/img', version: 1, page: null });
    expect(plan[1]).toEqual({ publicId: 'p/pdf', version: 1, page: 1 });
    expect(plan[4].page).toBe(4);
  });
  it('builds a signed small JPEG URL', () => {
    const u = readImageUrl({ cloudName: 'demo', apiKey: '1', apiSecret: 'abcd' }, 'p/pdf', 3, 2);
    expect(u).toContain('/image/authenticated/s--');
    expect(u).toContain('c_limit,h_1600,pg_2,w_1600');
    expect(u).toContain('q_70');
    expect(u).toMatch(/\.jpg(\?|$)/);
  });
  it('recognises real image bytes only', () => {
    expect(sniffMime(Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0]).toString('base64'))).toBe('image/jpeg');
    expect(sniffMime(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).toString('base64'))).toBe('image/png');
    expect(sniffMime(Buffer.from('RIFF\0\0\0\0WEBPVP8 ').toString('base64'))).toBe('image/webp');
    expect(sniffMime(Buffer.from('%PDF-1.7').toString('base64'))).toBeNull();
  });
});
