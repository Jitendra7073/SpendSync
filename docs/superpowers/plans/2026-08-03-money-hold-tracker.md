# Money Hold / IOU Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user optionally track "who owes me / who I owe" alongside any transaction, with an on-device reminder for the expected return date, surfaced on Home's balance card and a dedicated Holds list screen.

**Architecture:** Full-stack. Backend: a new `holds` table (Drizzle/Postgres) and `/api/holds` REST endpoints, following the exact `transactions` resource pattern already established (`withApi` wrapper, Zod validation, `holdService` class, `{success,data,error}` envelope). Android: new DTOs/Retrofit endpoints/repository methods, an inline toggle in the existing Add Transaction form, a new Holds list screen reached from Home (not a new bottom-nav tab), and on-device `WorkManager`-scheduled local notifications for reminders (reusing the dependency and channel/notifier pattern already built for transaction auto-capture).

**Tech Stack:** Next.js App Router + Drizzle ORM + Zod (backend, TypeScript); Kotlin + Jetpack Compose + Retrofit + WorkManager (Android).

Spec: `docs/superpowers/specs/2026-08-03-money-hold-tracker-design.md`

## Global Constraints (system design trade-offs — read before starting)

- **No DB actions against the live database.** Generating a migration file locally (`npm run db:generate`, pure code generation, no network) is fine; running `db:push`/`db:migrate` (applies to the live Postgres DB) is explicitly NOT this plan's job — the human runs that manually. If `db:generate` itself fails locally (e.g. no `DATABASE_URL` in this environment), skip it and say so — don't work around it by touching the DB another way.
- **Reminders are on-device only** (`WorkManager`, no server push). Accepted trade-off: a reminder doesn't survive an app reinstall or device switch, and `WorkManager`'s `setInitialDelay` is approximate under Doze/battery-optimization — acceptable for a "your money was due today" nudge, not acceptable if this were a time-critical alert.
- **`Hold.amount` is a snapshot taken at creation, not derived from the linked transaction.** Editing the transaction's amount later never retroactively changes what a hold expects back.
- **No pagination on `GET /api/holds`.** Per-user hold counts are expected to stay small (dozens, not thousands) — unlike `transactions`, which does paginate.
- **No backend tests for DB-dependent code.** No test database exists in this repo today, and standing one up is a separate initiative, not something to bundle silently into this feature. Only the Zod validation schemas (pure functions, zero DB dependency) get Vitest tests in this plan.
- **A hold can only be created when adding a NEW transaction, not when editing an existing one.** Editing a transaction never creates, updates, or touches an existing hold — that ambiguity (should the linked hold change too?) is out of scope; holds are managed from the Holds screen.
- **"Hold Money" (the net amount on hold) is computed client-side** by summing the pending-holds list already fetched for the Holds screen/Home card — no new backend aggregate endpoint, since the list is small enough that summing in Kotlin is simpler than adding and maintaining a second endpoint.
- Currency is always `₹` — reuse `com.example.spendsync.utils.formatInr(amount: Double): String` everywhere an amount is displayed. Never introduce a new currency symbol path.
- Follow existing code style: no KDoc on obvious code, comments only for non-obvious "why".

---

### Task 1: Backend — `holds` Drizzle schema

**Files:**
- Create: `backend/src/db/schema/holds.schema.ts`
- Modify: `backend/src/db/schema/index.ts`
- Modify: `backend/src/db/schema.ts` (the separate flat file `drizzle-kit` reads — **this duplication is a known trap in this codebase**: `drizzle.config.ts` points at `./src/db/schema.ts`, not the `schema/` barrel, so a table defined only in `schema/holds.schema.ts` is invisible to `drizzle-kit generate`)

**Interfaces:**
- Produces: `holds` table (Drizzle `pgTable`), `holdDirections = ['owed_to_me', 'owed_by_me'] as const`, `holdStatuses = ['pending', 'settled'] as const`, `type Hold`, `type NewHold`. Task 2 imports `holdDirections`/`holdStatuses` for its Zod enums; Task 3 imports `holds` for queries.

- [ ] **Step 1: Create the runtime schema file**

```ts
// backend/src/db/schema/holds.schema.ts
import { pgTable, uuid, text, decimal, timestamp } from 'drizzle-orm/pg-core';
import { user } from './auth.schema';
import { transactions } from './transactions.schema';

export const holdDirections = ['owed_to_me', 'owed_by_me'] as const;
export type HoldDirection = (typeof holdDirections)[number];

export const holdStatuses = ['pending', 'settled'] as const;
export type HoldStatus = (typeof holdStatuses)[number];

/**
 * Tracks money lent/borrowed alongside a transaction. `amount` is a
 * snapshot taken at creation — editing the linked transaction later never
 * retroactively changes what this hold expects back.
 */
export const holds = pgTable('holds', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  transactionId: uuid('transaction_id')
    .notNull()
    .references(() => transactions.id, { onDelete: 'cascade' }),
  direction: text('direction', { enum: holdDirections }).notNull(),
  personName: text('person_name').notNull(),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  expectedReturnDate: timestamp('expected_return_date').notNull(),
  status: text('status', { enum: holdStatuses }).notNull().default('pending'),
  settledAt: timestamp('settled_at'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});

export type Hold = typeof holds.$inferSelect;
export type NewHold = typeof holds.$inferInsert;
```

- [ ] **Step 2: Add the barrel export**

In `backend/src/db/schema/index.ts`, add one line (file currently exports `auth.schema`, `transactions.schema`, `categories.schema`, `budgets.schema`, `settings.schema` — append after the last line):

```ts
export * from './holds.schema';
```

- [ ] **Step 3: Mirror the table into the flat `drizzle-kit` schema file**

`backend/src/db/schema.ts` already imports `pgTable, text, timestamp, boolean, uuid, decimal` from `'drizzle-orm/pg-core'` — no new import needed. Append this block after the existing `budgets` table definition (before the `// USER SETTINGS TABLE` section comment):

```ts
export const holds = pgTable('holds', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  transactionId: uuid('transaction_id')
    .notNull()
    .references(() => transactions.id, { onDelete: 'cascade' }),
  direction: text('direction', { enum: ['owed_to_me', 'owed_by_me'] }).notNull(),
  personName: text('person_name').notNull(),
  amount: decimal('amount', { precision: 12, scale: 2 }).notNull(),
  expectedReturnDate: timestamp('expected_return_date').notNull(),
  status: text('status', { enum: ['pending', 'settled'] }).notNull().default('pending'),
  settledAt: timestamp('settled_at'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
  updatedAt: timestamp('updated_at').notNull().defaultNow(),
});
```

- [ ] **Step 4: Type-check**

Run (from `backend/`): `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Attempt migration generation (best-effort, per Global Constraints)**

Run (from `backend/`): `npm run db:generate`
- If it succeeds, a new file appears under `backend/drizzle/`. Do NOT run `db:push` or `db:migrate` — leave applying it to the human.
- If it fails because `DATABASE_URL` isn't set in this environment, that's expected — skip this step, note it in your report, and move on. Do not attempt to work around it by connecting to any database.

- [ ] **Step 6: Commit**

```bash
git add backend/src/db/schema/holds.schema.ts backend/src/db/schema/index.ts backend/src/db/schema.ts
git add backend/drizzle/ 2>/dev/null
git commit -m "feat(backend): add holds table schema"
```

---

### Task 2: Backend — Zod validation (`hold.types.ts`) with Vitest tests

**Files:**
- Create: `backend/src/types/hold.types.ts`
- Test: `backend/src/types/hold.types.test.ts`

**Interfaces:**
- Consumes: `holdDirections`, `holdStatuses` from `backend/src/db/schema/holds.schema.ts` (Task 1).
- Produces: `createHoldSchema`, `updateHoldSchema`, `holdQuerySchema`, `holdIdSchema` (Zod schemas), `type CreateHoldInput`, `type UpdateHoldInput`, `type HoldQuery`. Task 3 (service) and Task 4 (routes) both import these.

Use **relative imports** in both the source file and the test file (`../db/schema/holds.schema`, `./hold.types`), matching the existing `transaction.types.ts`'s convention — not the `@/` alias. No `vitest.config.ts` exists in this repo and none is needed for this: relative imports resolve under Vitest's zero-config defaults, while the `@/` alias would not.

- [ ] **Step 1: Write the failing tests**

```ts
// backend/src/types/hold.types.test.ts
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from `backend/`): `npx vitest run src/types/hold.types.test.ts`
Expected: FAIL — `hold.types.ts` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

```ts
// backend/src/types/hold.types.ts
import { z } from 'zod';
import { holdDirections, holdStatuses } from '../db/schema/holds.schema';

export const createHoldSchema = z.object({
  transactionId: z.string().uuid(),
  direction: z.enum(holdDirections),
  personName: z.string().min(1).max(200),
  amount: z
    .string()
    .or(z.number())
    .transform((val) => {
      const num = typeof val === 'string' ? parseFloat(val) : val;
      if (isNaN(num) || num <= 0) {
        throw new Error('Amount must be a positive number');
      }
      return num.toFixed(2);
    }),
  expectedReturnDate: z.string().datetime(),
});

export const updateHoldSchema = z.object({
  personName: z.string().min(1).max(200).optional(),
  expectedReturnDate: z.string().datetime().optional(),
  status: z.enum(holdStatuses).optional(),
});

export const holdQuerySchema = z.object({
  status: z.enum(holdStatuses).optional(),
  direction: z.enum(holdDirections).optional(),
});

export const holdIdSchema = z.object({
  id: z.string().uuid(),
});

export type CreateHoldInput = z.infer<typeof createHoldSchema>;
export type UpdateHoldInput = z.infer<typeof updateHoldSchema>;
export type HoldQuery = z.infer<typeof holdQuerySchema>;
```

- [ ] **Step 4: Run tests to verify they pass**

Run (from `backend/`): `npx vitest run src/types/hold.types.test.ts`
Expected: PASS (all 10 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/types/hold.types.ts backend/src/types/hold.types.test.ts
git commit -m "feat(backend): add hold validation schemas"
```

---

### Task 3: Backend — `hold.service.ts`

**Files:**
- Create: `backend/src/services/hold.service.ts`

**Interfaces:**
- Consumes: `holds` table (Task 1), `CreateHoldInput`/`UpdateHoldInput`/`HoldQuery` (Task 2), `NotFoundError` from `../utils/errors`, `db` from `../db/index`.
- Produces: `class HoldService { create, getAll, getById, update, delete }`, `export const holdService = new HoldService()`. Task 4 (routes) calls these directly.

No dedicated test — this is thin Drizzle query wrapping with no DB in this repo to test against (per Global Constraints). Verified by type-check only.

- [ ] **Step 1: Write the implementation**

```ts
// backend/src/services/hold.service.ts
import { eq, and, desc } from 'drizzle-orm';
import { db } from '../db/index';
import { holds } from '../db/schema/index';
import type { CreateHoldInput, UpdateHoldInput, HoldQuery } from '../types/hold.types';
import { NotFoundError } from '../utils/errors';

export class HoldService {
  async create(userId: string, data: CreateHoldInput) {
    const [hold] = await db
      .insert(holds)
      .values({
        userId,
        transactionId: data.transactionId,
        direction: data.direction,
        personName: data.personName,
        amount: data.amount,
        expectedReturnDate: new Date(data.expectedReturnDate),
      })
      .returning();

    return hold;
  }

  async getAll(userId: string, query: HoldQuery) {
    const conditions = [eq(holds.userId, userId)];
    if (query.status) conditions.push(eq(holds.status, query.status));
    if (query.direction) conditions.push(eq(holds.direction, query.direction));

    return db
      .select()
      .from(holds)
      .where(and(...conditions))
      .orderBy(desc(holds.createdAt));
  }

  async getById(userId: string, holdId: string) {
    const [hold] = await db
      .select()
      .from(holds)
      .where(and(eq(holds.id, holdId), eq(holds.userId, userId)));

    if (!hold) {
      throw new NotFoundError('Hold not found');
    }

    return hold;
  }

  async update(userId: string, holdId: string, data: UpdateHoldInput) {
    // Check it exists and belongs to this user before writing.
    await this.getById(userId, holdId);

    const { expectedReturnDate, status, ...rest } = data;

    const [updated] = await db
      .update(holds)
      .set({
        ...rest,
        ...(expectedReturnDate ? { expectedReturnDate: new Date(expectedReturnDate) } : {}),
        ...(status ? { status, settledAt: status === 'settled' ? new Date() : null } : {}),
        updatedAt: new Date(),
      })
      .where(and(eq(holds.id, holdId), eq(holds.userId, userId)))
      .returning();

    return updated;
  }

  async delete(userId: string, holdId: string) {
    await this.getById(userId, holdId);

    await db.delete(holds).where(and(eq(holds.id, holdId), eq(holds.userId, userId)));
  }
}

export const holdService = new HoldService();
```

- [ ] **Step 2: Type-check**

Run (from `backend/`): `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/services/hold.service.ts
git commit -m "feat(backend): add hold service"
```

---

### Task 4: Backend — `/api/holds` routes

**Files:**
- Create: `backend/src/app/api/holds/route.ts`
- Create: `backend/src/app/api/holds/[id]/route.ts`

**Interfaces:**
- Consumes: `withApi`/`corsPreflight` from `@/lib/api-handler`, `success`/`created`/`noContent` from `@/lib/response`, `holdService` (Task 3), `createHoldSchema`/`holdQuerySchema`/`updateHoldSchema`/`holdIdSchema` (Task 2).
- Produces: `POST /api/holds`, `GET /api/holds`, `PATCH /api/holds/[id]`, `DELETE /api/holds/[id]` — the exact endpoints Android Task 5 targets.

No dedicated test — same reasoning as Task 3 (no DB in this repo to exercise the route against). Verified by type-check only.

- [ ] **Step 1: Write the collection route**

```ts
// backend/src/app/api/holds/route.ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, created } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { createHoldSchema, holdQuerySchema } from '@/types/hold.types';

export const POST = withApi(
  async (request, { userId }) => {
    const body = await request.json();
    const data = createHoldSchema.parse(body);
    const hold = await holdService.create(userId, data);
    return created(hold);
  },
  { auth: 'required' }
);

export const GET = withApi(
  async (request, { userId }) => {
    const query = holdQuerySchema.parse(Object.fromEntries(request.nextUrl.searchParams));
    const holdsList = await holdService.getAll(userId, query);
    return success(holdsList);
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

- [ ] **Step 2: Write the single-item route**

```ts
// backend/src/app/api/holds/[id]/route.ts
import { withApi, corsPreflight } from '@/lib/api-handler';
import { success, noContent } from '@/lib/response';
import { holdService } from '@/services/hold.service';
import { holdIdSchema, updateHoldSchema } from '@/types/hold.types';

export const PATCH = withApi(
  async (request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    const data = updateHoldSchema.parse(await request.json());
    const hold = await holdService.update(userId, id, data);
    return success(hold);
  },
  { auth: 'required' }
);

export const DELETE = withApi(
  async (_request, { userId, params }) => {
    const { id } = holdIdSchema.parse(await params);
    await holdService.delete(userId, id);
    return noContent();
  },
  { auth: 'required' }
);

export { corsPreflight as OPTIONS };
```

- [ ] **Step 3: Type-check and build**

Run (from `backend/`): `npx tsc --noEmit`
Run (from `backend/`): `npm run build`
Expected: both succeed with no errors.

- [ ] **Step 4: Commit**

```bash
git add backend/src/app/api/holds/
git commit -m "feat(backend): add holds API routes"
```

---

### Task 5: Android — Hold DTOs and Retrofit endpoints

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/data/remote/model/AppModels.kt`
- Modify: `app/src/main/java/com/example/spendsync/data/remote/AppApiService.kt`

**Interfaces:**
- Produces: `data class HoldDto(id, userId, transactionId, direction, personName, amount, expectedReturnDate, status, settledAt, createdAt, updatedAt)`, `data class CreateHoldRequest(transactionId, direction, personName, amount: Double, expectedReturnDate)`, `data class UpdateHoldRequest(personName?, expectedReturnDate?, status?)`, plus `createHold`/`getHolds`/`updateHold`/`deleteHold` on the Retrofit service interface. Task 6 (`FinanceRepository`) calls these directly.

Before writing, open both files and find: (a) where `TransactionDto`/`CreateTransactionRequest`/`UpdateTransactionRequest` are defined in `AppModels.kt` (add the new classes directly after them), and (b) the exact response wrapper type names used by existing transaction endpoints in `AppApiService.kt` (this plan assumes `SuccessResponse<T>` and `SuccessResponseList<T>` based on the transactions block — confirm against the file and match exactly, including the transactions endpoints' exact `@Header`/`@Query`/`@Path` ordering, so the holds block reads as a natural continuation, not a different style).

- [ ] **Step 1: Add the DTOs to `AppModels.kt`**

Add directly after the existing `TransactionDto`/`CreateTransactionRequest`/`UpdateTransactionRequest` block:

```kotlin
data class HoldDto(
    @SerializedName("id")                 val id: String,
    @SerializedName("userId")             val userId: String,
    @SerializedName("transactionId")      val transactionId: String,
    @SerializedName("direction")          val direction: String, // "owed_to_me" or "owed_by_me"
    @SerializedName("personName")         val personName: String,
    @SerializedName("amount")             val amount: String, // decimal string, like TransactionDto.amount
    @SerializedName("expectedReturnDate") val expectedReturnDate: String,
    @SerializedName("status")             val status: String, // "pending" or "settled"
    @SerializedName("settledAt")          val settledAt: String?,
    @SerializedName("createdAt")          val createdAt: String,
    @SerializedName("updatedAt")          val updatedAt: String?,
)

data class CreateHoldRequest(
    @SerializedName("transactionId")      val transactionId: String,
    @SerializedName("direction")          val direction: String,
    @SerializedName("personName")         val personName: String,
    @SerializedName("amount")             val amount: Double,
    @SerializedName("expectedReturnDate") val expectedReturnDate: String,
)

data class UpdateHoldRequest(
    @SerializedName("personName")         val personName: String? = null,
    @SerializedName("expectedReturnDate") val expectedReturnDate: String? = null,
    @SerializedName("status")             val status: String? = null,
)
```

- [ ] **Step 2: Add the endpoints to `AppApiService.kt`**

Add directly after the existing transactions endpoints block, matching the exact response wrapper types and header/param style those use:

```kotlin
    @POST("api/holds")
    suspend fun createHold(
        @Header("Authorization") token: String,
        @Body request: CreateHoldRequest,
    ): Response<SuccessResponse<HoldDto>>

    @GET("api/holds")
    suspend fun getHolds(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("direction") direction: String? = null,
    ): Response<SuccessResponseList<HoldDto>>

    @PATCH("api/holds/{id}")
    suspend fun updateHold(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: UpdateHoldRequest,
    ): Response<SuccessResponse<HoldDto>>

    @DELETE("api/holds/{id}")
    suspend fun deleteHold(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<Unit>
```

If the file's actual response wrapper type names differ from `SuccessResponse<T>`/`SuccessResponseList<T>`, use whatever the transactions block actually uses instead — match it exactly, don't introduce a second naming convention.

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/spendsync/data/remote/model/AppModels.kt app/src/main/java/com/example/spendsync/data/remote/AppApiService.kt
git commit -m "feat: add hold DTOs and API endpoints"
```

---

### Task 6: Android — `FinanceRepository` hold methods

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/data/repository/FinanceRepository.kt`

**Interfaces:**
- Consumes: `HoldDto`/`CreateHoldRequest`/`UpdateHoldRequest` (Task 5), the file's own existing `getAuthHeader()`, `parseErrorMessage()`, `cached<T>()`, `cacheInvalidate()`, `cache` map, and `Exception.toUserMessage()` helpers (already present in this file — reuse them, don't reintroduce).
- Produces: `suspend fun createHold(transactionId, direction, personName, amount: Double, expectedReturnDate): AuthResult<HoldDto>`, `suspend fun getHolds(status: String? = null, direction: String? = null): AuthResult<List<HoldDto>>`, `suspend fun updateHold(id, status: String? = null, personName: String? = null, expectedReturnDate: String? = null): AuthResult<HoldDto>`, `suspend fun deleteHold(id): AuthResult<Unit>`. Tasks 9, 10, 11 call these directly.

- [ ] **Step 1: Add the four methods**

Add directly after the existing `createTransaction`/`updateTransaction`/`deleteTransaction` block, following that block's exact try/catch → `AuthResult` → `cacheInvalidate` shape:

```kotlin
    suspend fun createHold(
        transactionId: String,
        direction: String,
        personName: String,
        amount: Double,
        expectedReturnDate: String,
    ): AuthResult<HoldDto> {
        return try {
            val request = CreateHoldRequest(transactionId, direction, personName, amount, expectedReturnDate)
            val response = api.createHold(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun getHolds(status: String? = null, direction: String? = null): AuthResult<List<HoldDto>> {
        val key = "holds:$status:$direction"
        cached<List<HoldDto>>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getHolds(getAuthHeader(), status, direction)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun updateHold(
        id: String,
        status: String? = null,
        personName: String? = null,
        expectedReturnDate: String? = null,
    ): AuthResult<HoldDto> {
        return try {
            val request = UpdateHoldRequest(personName, expectedReturnDate, status)
            val response = api.updateHold(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteHold(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteHold(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("holds")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }
```

If `getAuthHeader()`/`parseErrorMessage()`/`toUserMessage()` have slightly different names in the actual file, use the real names — these four methods must match the file's own established helpers exactly, not reintroduce parallel ones.

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/spendsync/data/repository/FinanceRepository.kt
git commit -m "feat: add hold CRUD methods to FinanceRepository"
```

---

### Task 7: Android — `HoldReminderIds` and `HoldReminderNotifier`

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/HoldReminderIds.kt`
- Create: `app/src/main/java/com/example/spendsync/notifications/HoldReminderNotifier.kt`

**Interfaces:**
- Consumes: `formatInr(Double): String` from `com.example.spendsync.utils.CurrencyFormat`.
- Produces: `internal object HoldReminderIds { CHANNEL_ID, EXTRA_HOLD_ID }`, `object HoldReminderNotifier { fun postReminder(context, holdId, personName, amount, direction) }`. Task 8's worker calls `postReminder`.

No dedicated test — builds real `Notification` objects, requires the Android framework (same reasoning as `TransactionCaptureNotifier` in the prior feature). Verified manually alongside Task 11.

- [ ] **Step 1: Create the constants object**

```kotlin
package com.example.spendsync.notifications

internal object HoldReminderIds {
    const val CHANNEL_ID = "hold_reminder_channel"
    const val EXTRA_HOLD_ID = "hold_id"
}
```

- [ ] **Step 2: Create the notifier**

```kotlin
package com.example.spendsync.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.spendsync.R
import com.example.spendsync.utils.formatInr

object HoldReminderNotifier {

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                HoldReminderIds.CHANNEL_ID,
                "Money hold reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun postReminder(context: Context, holdId: String, personName: String, amount: Double, direction: String) {
        ensureChannel(context)

        val message = if (direction == "owed_to_me") {
            "$personName was expected to return ${formatInr(amount)} today"
        } else {
            "You were expected to pay back ${formatInr(amount)} to $personName today"
        }

        val notification = NotificationCompat.Builder(context, HoldReminderIds.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Money hold reminder")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(holdId.hashCode(), notification)
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/HoldReminderIds.kt app/src/main/java/com/example/spendsync/notifications/HoldReminderNotifier.kt
git commit -m "feat: add hold reminder notification builder"
```

---

### Task 8: Android — `HoldReminderScheduler` delay math (TDD) and `HoldReminderWorker`

**Files:**
- Create: `app/src/main/java/com/example/spendsync/notifications/HoldReminderScheduler.kt`
- Create: `app/src/main/java/com/example/spendsync/notifications/HoldReminderWorker.kt`
- Test: `app/src/test/java/com/example/spendsync/notifications/HoldReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `HoldReminderNotifier.postReminder(...)` (Task 7).
- Produces: `internal const val HOLD_REMINDER_HOUR = 9`, `internal fun computeReminderDelayMillis(targetDate: LocalDate, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long`, `class HoldReminderWorker : CoroutineWorker` with `companion object { fun schedule(context, holdId, personName, amount: Double, direction, expectedReturnDate: LocalDate); fun cancel(context, holdId) }`. Tasks 9 and 11 call `schedule`/`cancel`.

- [ ] **Step 1: Write the failing tests for the pure delay function**

```kotlin
package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HoldReminderSchedulerTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `computes positive delay for a future date`() {
        val now = ZonedDateTime.of(2026, 8, 3, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 5)

        val delay = computeReminderDelayMillis(target, now, zone)

        val expectedTarget = ZonedDateTime.of(2026, 8, 5, HOLD_REMINDER_HOUR, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expectedTarget - now, delay)
    }

    @Test
    fun `clamps to zero for a date in the past`() {
        val now = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 3)

        val delay = computeReminderDelayMillis(target, now, zone)

        assertEquals(0L, delay)
    }

    @Test
    fun `same-day before reminder hour gives a positive same-day delay`() {
        val now = ZonedDateTime.of(2026, 8, 5, 6, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 5)

        val delay = computeReminderDelayMillis(target, now, zone)

        val expectedTarget = ZonedDateTime.of(2026, 8, 5, HOLD_REMINDER_HOUR, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expectedTarget - now, delay)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.HoldReminderSchedulerTest"`
Expected: FAIL — `computeReminderDelayMillis`/`HOLD_REMINDER_HOUR` unresolved.

- [ ] **Step 3: Write `HoldReminderScheduler.kt` (the pure function)**

```kotlin
package com.example.spendsync.notifications

import java.time.LocalDate
import java.time.ZoneId

internal const val HOLD_REMINDER_HOUR = 9

/**
 * Delay from [nowMillis] to 9 AM local time on [targetDate]. Clamped to
 * zero for a target that's already passed — WorkManager rejects a negative
 * initialDelay, and firing "late" for an overdue hold is fine, it's not
 * meant to be exact-time-critical.
 */
internal fun computeReminderDelayMillis(
    targetDate: LocalDate,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val targetMillis = targetDate.atTime(HOLD_REMINDER_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    return (targetMillis - nowMillis).coerceAtLeast(0L)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.spendsync.notifications.HoldReminderSchedulerTest"`
Expected: PASS (all 3 tests)

- [ ] **Step 5: Write the worker (no test — Android framework dependent, same reasoning as `PendingCaptureRetryWorker`)**

```kotlin
package com.example.spendsync.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HoldReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val holdId = inputData.getString(KEY_HOLD_ID) ?: return Result.failure()
        val personName = inputData.getString(KEY_PERSON_NAME) ?: return Result.failure()
        val amount = inputData.getDouble(KEY_AMOUNT, 0.0)
        val direction = inputData.getString(KEY_DIRECTION) ?: return Result.failure()

        HoldReminderNotifier.postReminder(applicationContext, holdId, personName, amount, direction)
        return Result.success()
    }

    companion object {
        private const val KEY_HOLD_ID = "hold_id"
        private const val KEY_PERSON_NAME = "person_name"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DIRECTION = "direction"

        private fun workName(holdId: String) = "hold_reminder_$holdId"

        /**
         * One work item per hold (unlike PendingCaptureRetryWorker's single
         * global queue-flush worker) — REPLACE, not KEEP, since re-scheduling
         * the same hold (e.g. the user edits its return date) must supersede
         * any previously-queued reminder for it, not stack a second one.
         */
        fun schedule(
            context: Context,
            holdId: String,
            personName: String,
            amount: Double,
            direction: String,
            expectedReturnDate: LocalDate,
        ) {
            val delay = computeReminderDelayMillis(expectedReturnDate, System.currentTimeMillis())
            val data = workDataOf(
                KEY_HOLD_ID to holdId,
                KEY_PERSON_NAME to personName,
                KEY_AMOUNT to amount,
                KEY_DIRECTION to direction,
            )
            val request = OneTimeWorkRequestBuilder<HoldReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(holdId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, holdId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(holdId))
        }
    }
}
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/spendsync/notifications/HoldReminderScheduler.kt app/src/main/java/com/example/spendsync/notifications/HoldReminderWorker.kt app/src/test/java/com/example/spendsync/notifications/HoldReminderSchedulerTest.kt
git commit -m "feat: add hold reminder scheduling via WorkManager"
```

---

### Task 9: Android — Add Transaction: inline "expect this back?" toggle

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/transaction/AddExpenseScreen.kt`

**Interfaces:**
- Consumes: `FinanceRepository.createHold(...)` (Task 6), `HoldReminderWorker.schedule(...)` (Task 8), the file's own existing `MonthPickerDialog` composable, `TransactionType`, `type`/`note`/`amountVal`/`isEditing`/`editTransaction`/`toast`/`scope`/`today` state already in this file.

Before writing, read `app/src/main/java/com/example/spendsync/ui/shared/MonthPickerDialog.kt` to confirm its exact parameter names (this plan has only confirmed the call-site usage `MonthPickerDialog(current, maxDate, onConfirm, onDismiss)`, not the file's own signature — e.g. whether `maxDate` is nullable/optional, since this new date picker needs to allow FUTURE dates, unlike the existing transaction-date picker which caps at `today`). Also confirm whether `val context = LocalContext.current` already exists in this file (needed to call `HoldReminderWorker.schedule`); add the import and the val if missing.

- [ ] **Step 1: Add state**

Add alongside the existing `transactionDate`/`showDatePicker` state declarations:

```kotlin
var expectReturn by remember { mutableStateOf(false) }
var holdPersonName by remember { mutableStateOf("") }
var holdReturnDate by remember { mutableStateOf(today) }
var showHoldDatePicker by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add the toggle + conditional fields UI**

Insert directly after the note `OutlinedTextField` block (the one shown conditionally when `amount.isNotEmpty()`), inside the same `if (amount.isNotEmpty())` scope or as a sibling block gated the same way — and additionally gated on `!isEditing` per Global Constraints (holds are only created for new transactions):

```kotlin
if (!isEditing && amount.isNotEmpty()) {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (type == TransactionType.EXPENSE) "Expect this back?" else "Need to pay this back?",
            color = NeutralWhite,
            fontSize = 14.sp,
        )
        Switch(checked = expectReturn, onCheckedChange = { expectReturn = it })
    }
    if (expectReturn) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = holdPersonName,
            onValueChange = { holdPersonName = it },
            placeholder = { Text("Who's this with?", color = NeutralWhite.copy(alpha = 0.60f), fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showHoldDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeutralWhite)
            Spacer(Modifier.width(8.dp))
            Text("Expected return: $holdReturnDate", color = NeutralWhite, fontSize = 14.sp)
        }
    }
}
```

Match the `OutlinedTextField`'s `colors = OutlinedTextFieldDefaults.colors(...)` to whatever the note field directly above it uses — don't invent a different color scheme for this one field.

- [ ] **Step 3: Add the hold date-picker dialog**

Add alongside the existing `if (showDatePicker) { MonthPickerDialog(...) }` block:

```kotlin
if (showHoldDatePicker) {
    MonthPickerDialog(
        current = holdReturnDate,
        onConfirm = { picked -> holdReturnDate = picked; showHoldDatePicker = false },
        onDismiss = { showHoldDatePicker = false },
    )
}
```

This intentionally omits `maxDate` (or passes whatever "no cap" the real signature expects — confirm against the file per this task's opening instruction) since, unlike the transaction date, the expected return date is meant to be in the future.

- [ ] **Step 4: Wire hold creation into the Save handler**

In the existing `if (res is AuthResult.Success) { ... }` branch of the Save `onClick`, insert hold creation before the existing toast/delay/onBack lines:

```kotlin
if (res is AuthResult.Success) {
    if (!isEditing && expectReturn && holdPersonName.isNotBlank()) {
        val direction = if (type == TransactionType.EXPENSE) "owed_to_me" else "owed_by_me"
        val holdRes = financeRepository.createHold(
            transactionId = res.data.id,
            direction = direction,
            personName = holdPersonName,
            amount = amountVal,
            expectedReturnDate = "${holdReturnDate}T00:00:00.000Z",
        )
        if (holdRes is AuthResult.Success) {
            HoldReminderWorker.schedule(
                context = context,
                holdId = holdRes.data.id,
                personName = holdPersonName,
                amount = amountVal,
                direction = direction,
                expectedReturnDate = holdReturnDate,
            )
        } else {
            toast = ToastMessage(
                "Transaction saved, but couldn't track the hold: ${(holdRes as AuthResult.Error).message}",
                isError = true,
            )
            delay(1500)
            onBack()
            return@launch
        }
    }
    toast = ToastMessage(
        if (isEditing) "Transaction updated" else "Transaction added",
        isError = false
    )
    delay(500)
    onBack()
} else {
    val message = (res as AuthResult.Error).message
    toast = ToastMessage(message, isError = true)
}
```

`return@launch` here matches the coroutine `launch` block this Save handler already runs inside — confirm the exact label matches what's in the file (it should be the unlabeled `scope.launch { ... }` block, so `return@launch` is correct as-is).

- [ ] **Step 5: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/transaction/AddExpenseScreen.kt
git commit -m "feat: add expect-return-back toggle to Add Transaction"
```

---

### Task 10: Android — Home balance card, three-stat layout

**Files:**
- Modify: `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `FinanceRepository.getHolds(status: String?)` (Task 6), `formatInr` (existing).
- Produces: `HomeScreen(..., onOpenHolds: () -> Unit, ...)` — a new required parameter. Task 11 (MainScreen wiring) must pass this in, or the app won't compile after this task — expect that call site to be broken until Task 11 lands; that's fine, each task's compile check is scoped to `compileDebugKotlin` succeeding, which it will as long as Task 11 follows immediately after in the same session.

Before writing, re-read the current `HomeScreen.kt` around the balance card (roughly where `allTimeBalance`/`isBalanceLoading` state and the `Card` composable live) — this file has been touched by unrelated recent work, confirm line numbers before editing rather than trusting any cached line numbers.

- [ ] **Step 1: Add the `onOpenHolds` parameter**

Add to `HomeScreen`'s parameter list, alongside `onOpenSettings`:

```kotlin
    onOpenHolds: () -> Unit = {},
```

- [ ] **Step 2: Add hold-money state and fetch**

Add alongside the existing `allTimeBalance`/`isBalanceLoading` state and its `LaunchedEffect`:

```kotlin
var holdMoney by remember { mutableStateOf(0.0) }

LaunchedEffect(refreshKey, balanceRefreshKey) {
    when (val res = financeRepository.getHolds(status = "pending")) {
        is AuthResult.Success -> {
            holdMoney = res.data.sumOf { hold ->
                val amt = hold.amount.toDoubleOrNull() ?: 0.0
                if (hold.direction == "owed_to_me") amt else -amt
            }
        }
        is AuthResult.Error -> Unit
    }
}
```

This can share the existing `LaunchedEffect(refreshKey, balanceRefreshKey) { ... loadAllTimeBalance(...) ... }` block (add this fetch inside it) rather than adding a second `LaunchedEffect` with the same keys — check the existing block and fold this in rather than duplicating the effect trigger.

- [ ] **Step 3: Restructure the balance card**

Replace the card's `Column` content with the three-stat layout. The label at the top keeps using the existing localization key; "Net Balance" and "Hold Money" are new strings, left as plain English for now (matching how other newly-added UI strings in this codebase — e.g. the Automation settings section — were also left unlocalized, a deferred item rather than blocking on translating into all 5 supported languages):

```kotlin
Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
) {
    Text(
        text     = LocalizationUtils.getTranslation("total_balance", language),
        color    = NeutralWhite.copy(alpha = 0.80f),
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(6.dp))
    if (isBalanceLoading) {
        SkeletonLine(modifier = Modifier.width(160.dp), height = 32.dp)
    } else {
        Text(
            text       = formatInr((allTimeBalance ?: 0.0) + holdMoney),
            color      = NeutralWhite,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BrandYellow),
    )
    if (!isBalanceLoading) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Net Balance", color = NeutralWhite.copy(alpha = 0.70f), fontSize = 11.sp)
                Text(
                    text = formatInr(allTimeBalance ?: 0.0),
                    color = NeutralWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier.clickable { onOpenHolds() },
                horizontalAlignment = Alignment.End,
            ) {
                Text("Hold Money →", color = NeutralWhite.copy(alpha = 0.70f), fontSize = 11.sp)
                Text(
                    text = formatInr(holdMoney),
                    color = NeutralWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: this will FAIL at this point, because `MainScreen.kt`'s existing `HomeScreen(...)` call site doesn't yet pass `onOpenHolds` — that's fine, `onOpenHolds` has a `= {}` default, so it should actually still compile. Confirm it does; if there's a compile error, it's unrelated to the missing argument (which has a default) — investigate and fix before proceeding.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt
git commit -m "feat: show Net Balance / Hold Money / Total Balance on Home"
```

---

### Task 11: Android — Holds list screen and navigation wiring

**Files:**
- Create: `app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt`
- Modify: `app/src/main/java/com/example/spendsync/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `FinanceRepository.getHolds/updateHold` (Task 6), `HoldReminderWorker.cancel` (Task 8), `HomeScreen(..., onOpenHolds)` (Task 10), `formatInr`.
- Produces: `HoldsScreen(financeRepository, onBack: () -> Unit)` composable.

Before writing `MainScreen.kt` changes, re-read its current `expenseOverlayVisible`/`AnimatedContent` block (shown in this plan's research, but confirm line numbers/exact current state before editing) to mirror its slide animation pattern precisely, rather than inventing a different transition for this second overlay.

- [ ] **Step 1: Write the Holds screen**

```kotlin
package com.example.spendsync.ui.holds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.utils.formatInr
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun HoldsScreen(
    financeRepository: FinanceRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    var holds by remember { mutableStateOf<List<HoldDto>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        when (val res = financeRepository.getHolds()) {
            is AuthResult.Success -> holds = res.data
            is AuthResult.Error -> Unit
        }
    }

    fun markSettled(hold: HoldDto) {
        scope.launch {
            val res = financeRepository.updateHold(id = hold.id, status = "settled")
            if (res is AuthResult.Success) {
                HoldReminderWorker.cancel(context, hold.id)
                refreshKey++
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(NeutralOffWhite)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeutralBlack)
            }
            Spacer(Modifier.height(0.dp))
            Text("Holds", fontSize = 20.sp, color = NeutralBlack, modifier = Modifier.padding(start = 8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(holds) { hold ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(hold.personName, fontSize = 16.sp, color = NeutralBlack)
                            Text(formatInr(hold.amount.toDoubleOrNull() ?: 0.0), fontSize = 16.sp, color = NeutralBlack)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hold.direction == "owed_to_me") "Owed to you" else "You owe",
                            fontSize = 12.sp,
                            color = NeutralMid,
                        )
                        Text(
                            text = "Expected: ${hold.expectedReturnDate.take(10)} · ${hold.status}",
                            fontSize = 12.sp,
                            color = NeutralMid,
                        )
                        if (hold.status == "pending") {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { markSettled(hold) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            ) {
                                Text("Mark as settled")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Wire into `MainScreen.kt`**

Add state alongside `showTypeSheet`/`presetType`/`editingTransaction`:

```kotlin
var showHolds by rememberSaveable { mutableStateOf(false) }
```

Pass `onOpenHolds = { showHolds = true }` into the existing `HomeScreen(...)` call site (the `else -> HomeScreen(...)` branch inside the `HorizontalPager`).

Add a second overlay block mirroring the existing `AnimatedContent` add/edit-expense overlay (same `overlaySlideSpec`), placed as a sibling to it:

```kotlin
AnimatedContent(
    targetState    = showHolds,
    transitionSpec = {
        if (targetState) {
            slideInVertically(animationSpec = overlaySlideSpec) { it } togetherWith fadeOut(tween(0))
        } else {
            fadeIn(tween(0)) togetherWith
                slideOutVertically(animationSpec = overlaySlideSpec) { it }
        }
    },
    label = "holds_overlay",
) { visible ->
    if (visible) {
        HoldsScreen(
            financeRepository = financeRepository,
            onBack = { showHolds = false },
        )
    }
}
```

Reuse the exact `overlaySlideSpec` `val` already defined for the expense overlay — don't redefine a second `spring<IntOffset>(...)` with the same parameters.

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass (including the 3 new `HoldReminderSchedulerTest` cases from Task 8).

- [ ] **Step 5: Manually verify on-device**

Run: `/run` (or `./gradlew installDebug` + launch manually)

1. Add a new expense transaction, toggle "Expect this back?" on, fill in a person name and a future return date, save. Confirm the transaction appears on Home as usual.
2. Check Home's balance card: confirm it now shows Total Balance (big), and Net Balance / Hold Money as sub-stats, with Hold Money reflecting the new pending hold.
3. Tap "Hold Money" — confirm the Holds screen opens and shows the new entry with the right person/amount/date/status.
4. Tap "Mark as settled" — confirm the row updates and Home's Hold Money figure drops accordingly on return.
5. Add an income transaction with the toggle on — confirm it creates an `owed_by_me` hold ("You owe") and that Hold Money nets correctly against the earlier `owed_to_me` one.
6. Edit an existing transaction — confirm the toggle does NOT appear (holds are create-only, per this plan's scope).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/spendsync/ui/holds/HoldsScreen.kt app/src/main/java/com/example/spendsync/ui/main/MainScreen.kt
git commit -m "feat: add Holds list screen and Home navigation"
```
