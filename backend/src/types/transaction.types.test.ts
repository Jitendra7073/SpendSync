import { describe, it, expect } from 'vitest';
import { createTransactionSchema, updateTransactionSchema } from './transaction.types';

const base = { amount: 10, type: 'debit', merchant: 'Cafe', category: 'Food' };

describe('client-made transaction id (offline outbox)', () => {
  it('create keeps a uuid id and rejects anything else', () => {
    const id = '123e4567-e89b-12d3-a456-426614174000';
    expect(createTransactionSchema.parse({ ...base, id }).id).toBe(id);
    expect(() => createTransactionSchema.parse({ ...base, id: 'nope' })).toThrow();
  });

  it('update never carries an id, so a PATCH cannot change the primary key', () => {
    const parsed = updateTransactionSchema.parse({ id: '123e4567-e89b-12d3-a456-426614174000', note: 'x' });
    expect(parsed).not.toHaveProperty('id');
  });
});
