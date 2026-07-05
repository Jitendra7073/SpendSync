# ⚡ Quick Start: Developer Mode

## What is Developer Mode?

Developer mode is a user setting that:
- ✅ Auto-verifies emails (no SMTP needed)
- ✅ Makes testing faster
- ✅ Can be enabled/disabled per user

## 🚀 Quick Setup

### Step 1: Run Migration
```bash
cd backend
npm run db:push
```

### Step 2: Restart Server
```bash
npm run dev
```

### Step 3: Test It!

#### Register User:
```http
POST http://localhost:3000/api/auth/sign-up/email

{
  "email": "test@example.com",
  "password": "SecurePass123!",
  "name": "Test User"
}
```

#### Login:
```http
POST http://localhost:3000/api/auth/sign-in/email

{
  "email": "test@example.com",
  "password": "SecurePass123!"
}
```

**Save the session cookie from login response!**

#### Enable Developer Mode:
```http
POST http://localhost:3000/api/settings/developer-mode
Cookie: <your_session_cookie>

{
  "enabled": true
}
```

## 📡 All Settings Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/settings` | Get user settings |
| PATCH | `/api/settings` | Update settings |
| GET | `/api/settings/developer-mode` | Check if enabled |
| POST | `/api/settings/developer-mode` | Toggle on/off |

## ✨ Features

- **Per-User:** Each user has own settings
- **Auto-Created:** Settings created on first access
- **Flexible:** Add more settings easily

## 📝 Note

**Email verification is already disabled by default** in Better Auth config for easier development. Developer mode is an optional feature for future use when you enable email verification.

## 🎯 Current Behavior

Right now, with `requireEmailVerification: false`:
- ✅ All users can register and login immediately
- ✅ No email verification needed
- ✅ No 403 errors
- ✅ Developer mode is ready for when you need it

## 🔜 When You Need Developer Mode

When you enable email verification in production:
1. Set `requireEmailVerification: true` in auth config
2. Configure SMTP in `.env`
3. Test users can enable developer mode to bypass verification
4. Production users go through normal verification

---

**Status:** Run `npm run db:push` and restart server! 🚀
