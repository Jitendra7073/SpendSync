import { describe, it, expect } from 'vitest';
import { createHoldSchema, updateHoldSchema, holdQuerySchema, holdIdSchema } from './hold.types';

describe('createHoldSchema', () => {
  it('accepts a valid payload and coerces amount to a 2-decimal string', () => {
    const result = createHoldSchema.parse({
      transactionId: '123e4567-e89b-12d3-a456-426614174000',
      direction: 'owed_to_me',
      personName: 'Manish',
      amount: 500,
      expectedReturnDate: '2026-08-10T00:00:00.000Z',
    });
    expect(result.amount).toBe('500.00');
    expect(result.direction).toBe('owed_to_me');
  });

  it('rejects a non-uuid transactionId', () => {
    expect(() =>
      createHoldSchema.parse({
        transactionId: 'not-a-uuid',
        direction: 'owed_to_me',
        personName: 'Manish',
        amount: 500,
        expectedReturnDate: '2026-08-10T00:00:00.000Z',
      })
    ).toThrow();
  });

  it('rejects a direction outside the enum', () => {
    expect(() =>
      createHoldSchema.parse({
        transactionId: '123e4567-e89b-12d3-a456-426614174000',
        direction: 'sideways',
        personName: 'Manish',
        amount: 500,
        expectedReturnDate: '2026-08-10T00:00:00.000Z',
      })
    ).toThrow();
  });

  it('rejects a zero or negative amount', () => {
    expect(() =>
      createHoldSchema.parse({
        transactionId: '123e4567-e89b-12d3-a456-426614174000',
        direction: 'owed_to_me',
        personName: 'Manish',
        amount: 0,
        expectedReturnDate: '2026-08-10T00:00:00.000Z',
      })
    ).toThrow();
  });

  it('rejects a blank personName', () => {
    expect(() =>
      createHoldSchema.parse({
        transactionId: '123e4567-e89b-12d3-a456-426614174000',
        direction: 'owed_to_me',
        personName: '',
        amount: 500,
        expectedReturnDate: '2026-08-10T00:00:00.000Z',
      })
    ).toThrow();
  });
});

describe('updateHoldSchema', () => {
  it('accepts a status-only update', () => {
    const result = updateHoldSchema.parse({ status: 'settled' });
    expect(result.status).toBe('settled');
  });

  it('accepts an empty object (every field optional)', () => {
    expect(updateHoldSchema.parse({})).toEqual({});
  });

  it('rejects a status outside the enum', () => {
    expect(() => updateHoldSchema.parse({ status: 'archived' })).toThrow();
  });
});

describe('holdQuerySchema', () => {
  it('accepts no filters', () => {
    expect(holdQuerySchema.parse({})).toEqual({});
  });

  it('accepts valid status and direction filters', () => {
    const result = holdQuerySchema.parse({ status: 'pending', direction: 'owed_by_me' });
    expect(result).toEqual({ status: 'pending', direction: 'owed_by_me' });
  });
});

describe('holdIdSchema', () => {
  it('accepts a valid uuid', () => {
    expect(holdIdSchema.parse({ id: '123e4567-e89b-12d3-a456-426614174000' })).toEqual({
      id: '123e4567-e89b-12d3-a456-426614174000',
    });
  });

  it('rejects a non-uuid id', () => {
    expect(() => holdIdSchema.parse({ id: 'nope' })).toThrow();
  });
});
