# 🔧 Better Auth URL Error - Fixed

## Issue
```
TypeError: Invalid URL
at new URL (node:internal/url:806:29)
input: '/api/auth/*'
```

## Root Cause
Better Auth's handler was receiving the wildcard pattern `/api/auth/*` as the URL instead of the actual request path. This happened because of how Express was mounting the Better Auth handler.

## Solution Applied

### 1. Added `basePath` to Better Auth Config
**File**: `src/config/auth.ts`

```typescript
export const auth = betterAuth({
  // ... other config
  baseURL: config.apiUrl,
  basePath: '/api/auth',  // ✅ Added this
  // ... rest of config
});
```

### 2. Fixed Route Handler in Server
**File**: `src/server.ts`

**Before** (Wrong):
```typescript
app.all('/api/auth/**', async (req, res) => {
  return auth.handler(req, res);
});
```

**After** (Correct):
```typescript
app.all('/api/auth*', (req, res) => {
  return auth.handler(req, res);
});
```

The key change: Use `/api/auth*` (matches `/api/auth` and `/api/auth/anything`) instead of `/api/auth/**` or `/api/auth/*`.

## How to Test

### 1. Restart the Server
```bash
# Stop the server (Ctrl+C)
# Start again
npm run dev
```

You should see:
```
2026-07-04 17:12:45 [info]: 🚀 Server started successfully
2026-07-04 17:12:45 [info]: 📦 Environment: development
2026-07-04 17:12:45 [info]: 🌐 API URL: http://localhost:3000
```

**No errors!** ✅

### 2. Test Health Endpoint
```bash
curl http://localhost:3000/health
```

Expected:
```json
{"status":"ok","timestamp":"...","environment":"development"}
```

### 3. Test Better Auth Endpoints

**Sign Up:**
```bash
curl -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!",
    "name": "Test User"
  }'
```

**Sign In:**
```bash
curl -X POST http://localhost:3000/api/auth/sign-in/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
  }' \
  -c cookies.txt -v
```

### 4. Automated Test Script
```bash
node test-endpoints.mjs
```

This will test all endpoints and show you if everything is working.

## Better Auth Endpoints

With `basePath: '/api/auth'`, Better Auth automatically handles:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/auth/sign-up/email` | POST | Register with email/password |
| `/api/auth/sign-in/email` | POST | Login with email/password |
| `/api/auth/sign-out` | POST | Logout |
| `/api/auth/session` | GET | Get current session |
| `/api/auth/verify-email` | POST | Verify email address |
| `/api/auth/forget-password` | POST | Request password reset |
| `/api/auth/reset-password` | POST | Reset password with token |

## What Changed

### Files Modified:
1. ✅ `src/config/auth.ts` - Added `basePath: '/api/auth'`
2. ✅ `src/server.ts` - Changed route pattern from `/api/auth/**` to `/api/auth*`
3. ✅ Created `test-endpoints.mjs` - Automated endpoint testing

### Why This Works:
- `basePath` tells Better Auth what prefix to expect
- `/api/auth*` in Express matches any route starting with `/api/auth`
- Better Auth receives the full request path and handles routing internally
- No URL parsing errors because Better Auth gets valid request objects

## Verification Checklist

- [x] Server starts without "Invalid URL" error
- [x] Health check endpoint works
- [ ] Can register a user (test manually)
- [ ] Can login (test manually)
- [ ] Session persists in cookies
- [ ] Protected endpoints require authentication

## Common Patterns to Avoid

### ❌ Don't use these patterns:
```typescript
// These cause URL parsing errors:
app.use('/api/auth/*', ...)      // Express doesn't pass correct path
app.all('/api/auth/**', ...)     // Wildcard patterns don't work
app.all('/api/auth/*/*', ...)    // Too specific
```

### ✅ Use this pattern:
```typescript
// This works correctly:
app.all('/api/auth*', (req, res) => {
  return auth.handler(req, res);
});
```

## Next Steps

1. **Test authentication flow:**
   - Register a user
   - Login
   - Check session
   - Logout

2. **Test protected endpoints:**
   - Try accessing `/api/transactions` without login (should fail)
   - Login and try again (should work)

3. **Verify database:**
   - Check `user` table has the registered user
   - Check `session` table has active sessions

```bash
# Open database GUI
npm run db:studio
```

## Still Having Issues?

If you still see errors:

1. **Check .env file:**
   ```env
   DATABASE_URL=postgresql://...  # Must be valid
   AUTH_SECRET=...                # Must be 32+ chars
   API_URL=http://localhost:3000  # Must match your server
   ```

2. **Clear node_modules and reinstall:**
   ```bash
   rm -rf node_modules package-lock.json
   npm install
   ```

3. **Check logs:**
   ```bash
   tail -f logs/error.log
   ```

4. **Test database connection:**
   ```bash
   npm run db:studio
   ```

## Success!

If you can:
- ✅ Start server without errors
- ✅ GET `/health` returns OK
- ✅ POST to `/api/auth/sign-up/email` accepts requests
- ✅ No "Invalid URL" errors in console

**Then everything is working correctly!** 🎉

---

**Status**: Fixed! Better Auth is now properly integrated with Express.
