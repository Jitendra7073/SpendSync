# 🔧 Better Auth "Invalid URL" Error - Complete Fix

## The Problem

When hitting Better Auth endpoints, you get:
```
TypeError: Invalid URL
at new URL (node:internal/url:806:29)
code: 'ERR_INVALID_URL',
input: '/api/auth/register'
```

## Root Cause

Better Auth v1.x uses the Web Fetch API (Request/Response objects) internally, while Express uses its own req/res objects. Better Auth's router tries to create a URL object from the request path, but it receives a relative path like `/api/auth/register` instead of a full URL like `http://localhost:3000/api/auth/register`.

## The Solution

We need to convert Express's request/response objects to Web API Request/Response objects that Better Auth expects.

### Changes Made

#### 1. Simplified Better Auth Config (`src/config/auth.ts`)

```typescript
export const auth = betterAuth({
  database: drizzleAdapter(db, {
    provider: 'pg',
  }),
  
  emailAndPassword: {
    enabled: true,
    requireEmailVerification: false,
  },
  
  session: {
    expiresIn: 60 * 60 * 24 * 7, // 7 days
    updateAge: 60 * 60 * 24, // 1 day
  },
  
  baseURL: config.apiUrl, // http://localhost:3000
  secret: config.auth.secret,
  trustedOrigins: config.cors.allowedOrigins,
});
```

Key point: `baseURL` must be a full URL with protocol and host.

#### 2. Fixed Express Integration (`src/server.ts`)

```typescript
app.all('/api/auth/*', async (req, res) => {
  try {
    // Construct full URL
    const url = `${req.protocol}://${req.get('host')}${req.originalUrl}`;
    
    // Prepare body (Express already parsed it)
    let body = undefined;
    if (req.method !== 'GET' && req.method !== 'HEAD' && req.body) {
      body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    }
    
    // Create Web API Request
    const webRequest = new Request(url, {
      method: req.method,
      headers: req.headers as HeadersInit,
      body: body,
    });
    
    // Call Better Auth handler
    const webResponse = await auth.handler(webRequest);
    
    // Convert response back to Express
    res.status(webResponse.status);
    webResponse.headers.forEach((value, key) => {
      res.setHeader(key, value);
    });
    const text = await webResponse.text();
    res.send(text);
  } catch (error) {
    console.error('Better Auth handler error:', error);
    res.status(500).json({ error: 'Authentication service error' });
  }
});
```

## Why This Works

1. **Full URL Construction**: `${req.protocol}://${req.get('host')}${req.originalUrl}` creates a valid URL like `http://localhost:3000/api/auth/sign-up/email`

2. **Web API Request**: Better Auth expects a standard Web API `Request` object, not Express's `req`

3. **Body Handling**: Express already parsed the JSON body, so we stringify it back for the Web API Request

4. **Response Conversion**: Better Auth returns a Web API `Response`, which we convert back to Express's `res`

## Files Modified

- ✅ `src/config/auth.ts` - Removed `basePath` (not needed)
- ✅ `src/server.ts` - Added proper Express ↔ Web API conversion
- ✅ Created `TEST_COMMANDS.md` - Test commands

## How to Test

### 1. Restart Server
```bash
# Stop with Ctrl+C
npm run dev
```

Server should start without errors.

### 2. Test Health Check
```bash
curl http://localhost:3000/health
```

Expected:
```json
{"status":"ok","timestamp":"...","environment":"development"}
```

### 3. Register a User
```bash
curl -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!","name":"Test User"}'
```

Expected (on first registration):
```json
{
  "user": {
    "id": "...",
    "email": "test@example.com",
    "name": "Test User",
    "emailVerified": false
  },
  "session": {
    "id": "...",
    "userId": "...",
    "expiresAt": "..."
  }
}
```

### 4. Login
```bash
curl -X POST http://localhost:3000/api/auth/sign-in/email \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}' \
  -c cookies.txt -v
```

Expected:
- Status: 200
- Set-Cookie header with session token
- User and session data in response

### 5. Test Protected Endpoint
```bash
curl http://localhost:3000/api/transactions \
  -b cookies.txt
```

Expected:
```json
{
  "success": true,
  "data": [],
  "meta": {"page": 1, "limit": 50, "total": 0}
}
```

## Better Auth Endpoints

With this setup, all Better Auth endpoints work:

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/auth/sign-up/email` | Register with email/password |
| POST | `/api/auth/sign-in/email` | Login with email/password |
| POST | `/api/auth/sign-out` | Logout current session |
| GET | `/api/auth/session` | Get current session info |
| POST | `/api/auth/forget-password` | Request password reset |
| POST | `/api/auth/reset-password` | Reset password with token |
| POST | `/api/auth/verify-email` | Verify email address |

## Common Issues

### Issue 1: Still Getting "Invalid URL"
**Check**: Is `API_URL` in `.env` a full URL?
```env
# ✅ Correct
API_URL=http://localhost:3000

# ❌ Wrong
API_URL=/api
API_URL=localhost:3000
```

### Issue 2: CORS Errors
**Solution**: Make sure CORS origin matches your request origin:
```env
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

### Issue 3: Body Parsing Issues
**Check**: Make sure these middleware are loaded BEFORE Better Auth handler:
```typescript
app.use(express.json());        // ✅ Must come first
app.use(express.urlencoded());  // ✅ Must come first
app.all('/api/auth/*', ...);    // Then Better Auth
```

### Issue 4: Headers Not Copied Correctly
Better Auth might set cookies or other headers. Make sure we're copying all headers:
```typescript
webResponse.headers.forEach((value, key) => {
  res.setHeader(key, value);
});
```

## Debugging Tips

### 1. Check the constructed URL
Add logging:
```typescript
const url = `${req.protocol}://${req.get('host')}${req.originalUrl}`;
console.log('Better Auth URL:', url);
```

Should log: `Better Auth URL: http://localhost:3000/api/auth/sign-up/email`

### 2. Check the request body
```typescript
console.log('Request body:', req.body);
```

Should log: `{ email: '...', password: '...', name: '...' }`

### 3. Check Better Auth response
```typescript
const webResponse = await auth.handler(webRequest);
console.log('Better Auth status:', webResponse.status);
console.log('Better Auth headers:', Object.fromEntries(webResponse.headers));
```

### 4. Test with curl verbose
```bash
curl -v -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test123!","name":"Test"}'
```

Look for:
- Request headers being sent
- Response status code
- Response headers (especially Set-Cookie)
- Response body

## Success Criteria

✅ Server starts without errors
✅ Health check returns 200
✅ Can register a user (returns 200 with user data)
✅ Can login (returns 200 with session)
✅ Session cookie is set (check Set-Cookie header)
✅ Protected endpoints work with cookie
✅ Can logout

## What We Learned

1. Better Auth uses Web API standards (Request/Response)
2. Express uses its own request/response objects
3. We need to convert between the two
4. Full URLs are required (not relative paths)
5. Body needs to be a string for Web API Request
6. Headers need to be copied from Web Response to Express response

## Alternative Approach (Advanced)

If you want a cleaner integration, you could use a middleware adapter:

```typescript
// Create a Better Auth adapter for Express
function betterAuthExpress(auth) {
  return async (req, res, next) => {
    if (!req.path.startsWith('/api/auth')) {
      return next();
    }
    
    const url = `${req.protocol}://${req.get('host')}${req.originalUrl}`;
    const body = req.body ? JSON.stringify(req.body) : undefined;
    
    try {
      const webRequest = new Request(url, {
        method: req.method,
        headers: req.headers,
        body,
      });
      
      const webResponse = await auth.handler(webRequest);
      
      res.status(webResponse.status);
      webResponse.headers.forEach((v, k) => res.setHeader(k, v));
      res.send(await webResponse.text());
    } catch (error) {
      next(error);
    }
  };
}

// Usage
app.use(betterAuthExpress(auth));
```

---

**Status**: Fixed! Better Auth now properly integrates with Express using Web API conversion.
