# 🔧 Registration "Internal Server Error" Fix

## The Problem

When you register a user:
1. ✅ User is created in database
2. ❌ Server returns "Internal Server Error"
3. ❌ Next attempt shows "User already exists"

This means registration **partially works** but fails when sending the response.

## 🛠️ Solution Steps

### Step 1: Restart the Server

The server code has been updated with better error handling.

```bash
# Stop the server (Ctrl+C)
npm run dev
```

### Step 2: Clean Up Existing Failed User

Since the user was created but registration failed, you need to either:

**Option A: Login with that user** (if you remember the password)
```bash
POST http://localhost:3000/api/auth/sign-in/email
{
  "email": "sutharjitendra529@gmail.com",
  "password": "password123"
}
```

**Option B: Use a different email for testing**
```bash
POST http://localhost:3000/api/auth/sign-up/email
{
  "email": "test@example.com",
  "password": "Test123!",
  "name": "Test User"
}
```

**Option C: Delete the user from database**

Using Drizzle Studio (GUI):
```bash
npm run db:studio
```
Then:
1. Open `https://local.drizzle.studio`
2. Find the `user` table
3. Delete the user with email "sutharjitendra529@gmail.com"

### Step 3: Try Registration Again

After restarting the server, try registering:

```json
POST http://localhost:3000/api/auth/sign-up/email

{
  "email": "new@example.com",
  "password": "SecurePass123!",
  "name": "New User"
}
```

### Step 4: Check Server Console

After the request, check your terminal where the server is running. You should see:

```
2026-07-04 17:45:00 [info]: Better Auth request: POST /api/auth/sign-up/email
2026-07-04 17:45:01 [info]: Better Auth response: 200
```

If you see an error, it will show the detailed error message.

## 🧪 Using the Test Script

I've created a PowerShell script to help test:

```powershell
cd backend
.\test-user.ps1
```

This interactive script lets you:
1. Register new users
2. Login
3. Test if user exists
4. Clean up database

## 📋 What Was Fixed

Updated `src/server.ts` to:
- ✅ Add better logging for Better Auth requests
- ✅ Properly parse JSON responses
- ✅ Show detailed errors in development mode
- ✅ Handle both JSON and text responses

## 🔍 Debugging Steps

### Check 1: Server Logs
```bash
# In the terminal where server is running
# Look for errors after making the request
```

### Check 2: Database
```bash
npm run db:studio
```
Check if:
- User table exists
- User was created
- Session table exists

### Check 3: Postman Response
After registration fails, check:
- Status code (should see 500)
- Response body (should see error details)
- Headers

### Check 4: Try Different Password

Sometimes Better Auth rejects weak passwords. Try:
```json
{
  "email": "test@example.com",
  "password": "StrongPass123!@#",
  "name": "Test User"
}
```

## 🎯 Quick Test Sequence

### Test 1: Health Check
```
GET http://localhost:3000/health
```
Expected: `{"status":"ok",...}`

### Test 2: Register NEW user
```
POST http://localhost:3000/api/auth/sign-up/email
{
  "email": "fresh@example.com",
  "password": "Test123!Pass",
  "name": "Fresh User"
}
```

### Test 3: Check Server Console
Look for:
```
[info]: Better Auth request: POST /api/auth/sign-up/email
[info]: Better Auth response: 200
```

Or error:
```
[error]: Better Auth handler error: ...
```

### Test 4: If Success, Try Login
```
POST http://localhost:3000/api/auth/sign-in/email
{
  "email": "fresh@example.com",
  "password": "Test123!Pass"
}
```

## ⚠️ Common Causes

### Cause 1: Response Already Sent
Sometimes Express middleware tries to send response twice.
**Fixed by**: Using `res.json()` instead of `res.send(text)`

### Cause 2: Headers Already Sent
Headers set twice causing error.
**Fixed by**: Proper response handling

### Cause 3: JSON Parse Error
Better Auth response not valid JSON.
**Fixed by**: Try-catch around JSON parsing

### Cause 4: Database Connection Lost
Connection dropped during session creation.
**Solution**: Check DATABASE_URL is correct

## 📞 Next Steps

1. **Restart server** → `npm run dev`
2. **Try with NEW email** → Different from failed one
3. **Watch console** → Look for detailed error
4. **Share error** → If still failing, share the server console error

## 💡 Pro Tips

- Use **different email** for each test to avoid "user exists" error
- Check **server console** for real error messages
- Use **strong passwords** (8+ chars, mixed case, numbers, symbols)
- **Clean database** between tests if needed

---

**Status**: Server code updated with better error handling. Restart and try again!
