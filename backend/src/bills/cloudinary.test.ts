import { createHash } from 'node:crypto';
import { v2 as cloudinary } from 'cloudinary';
import { describe, expect, it } from 'vitest';
import { deliveryUrls, uploadParams, verifyUploadResponse, verifyWebhook } from './cloudinary';

const c = { cloudName: 'demo', apiKey: '1234', apiSecret: 'abcd' };

describe('upload signing', () => {
  it('uses Cloudinary signing (documented example)', () => {
    // https://cloudinary.com/documentation/authentication_signatures — api_secret "abcd"
    expect(
      cloudinary.utils.api_sign_request({ eager: 'w_400,h_300,c_pad|w_260,h_200,c_crop', public_id: 'sample_image', timestamp: '1315060510' }, 'abcd'),
    ).toBe('bfd09f95f331f558cbd1320e67aa8d488770583e');
  });

  it('signs exactly the restricted params, all as strings, without the api key', () => {
    const { uploadUrl, params } = uploadParams(c, 'spendsync/bills/u1/x', 'https://api.test/api/bills/webhook', 1700000000);
    expect(uploadUrl).toBe('https://api.cloudinary.com/v1_1/demo/image/upload');
    expect(params).toMatchObject({ type: 'authenticated', overwrite: 'false', allowed_formats: 'jpg,png,webp,heic,pdf', timestamp: '1700000000', api_key: '1234' });
    const signed: Record<string, string> = { ...params };
    delete signed.signature;
    delete signed.api_key; // the key is sent but never signed
    expect(params.signature).toBe(cloudinary.utils.api_sign_request(signed, 'abcd'));
    expect(Object.values(params).every((v) => typeof v === 'string')).toBe(true);
  });
});

describe('verifying Cloudinary', () => {
  const sig = (p: object) => cloudinary.utils.api_sign_request(p, 'abcd');

  it('accepts a genuine upload response and rejects tampering', () => {
    const r = { public_id: 'spendsync/bills/u1/x', version: 1700000001, signature: sig({ public_id: 'spendsync/bills/u1/x', version: 1700000001 }) };
    expect(verifyUploadResponse(c, r)).toBe(true);
    expect(verifyUploadResponse(c, { ...r, public_id: 'spendsync/bills/u2/x' })).toBe(false);
    expect(verifyUploadResponse(c, { ...r, version: 1 })).toBe(false);
    expect(verifyUploadResponse(c, { ...r, signature: 'x' })).toBe(false);
  });

  it('accepts a fresh webhook and rejects tampered, stale or unsigned ones', () => {
    const body = '{"notification_type":"upload","public_id":"a"}';
    const ts = '1700000000';
    const good = createHash('sha1').update(body + ts + 'abcd').digest('hex');
    expect(verifyWebhook(c, body, ts, good, 1700000100)).toBe(true);
    expect(verifyWebhook(c, body + ' ', ts, good, 1700000100)).toBe(false);
    expect(verifyWebhook(c, body, ts, good, 1700000000 + 7201)).toBe(false);
    expect(verifyWebhook(c, body, null, good, 1700000100)).toBe(false);
    expect(verifyWebhook(c, body, ts, null, 1700000100)).toBe(false);
  });
});

describe('delivery URLs', () => {
  it('are signed authenticated URLs with the right transformations', () => {
    const u = deliveryUrls(c, { publicId: 'spendsync/bills/u1/x', version: 1700000001, format: 'jpg', pages: null });
    for (const url of [u.thumb, u.full, u.blurred]) {
      expect(url).toContain('https://res.cloudinary.com/demo/image/authenticated/s--');
      expect(url).toContain('v1700000001');
    }
    expect(u.thumb).toContain('c_fill,h_300,w_300');
    expect(u.blurred).toContain('e_blur:2000');
    expect(u.pageUrls).toEqual([u.full]);
    expect(u.original).toBeNull();
  });

  it('render PDF pages as images and keep the original', () => {
    const u = deliveryUrls(c, { publicId: 'spendsync/bills/u1/p', version: 1, format: 'pdf', pages: 3 });
    expect(u.thumb).toContain('pg_1');
    expect(u.thumb).toMatch(/\.jpg(\?|$)/);
    expect(u.pageUrls).toHaveLength(3);
    expect(u.pageUrls[2]).toContain('pg_3');
    expect(u.original).toMatch(/\.pdf(\?|$)/);
  });
});
