# 🎮 Developer Mode & Settings System

## Overview

The settings system allows users to configure application behavior, including a **Developer Mode** that automatically verifies emails, making testing easier.

## ✨ Features

- ✅ **Developer Mode** - Auto-verify emails (no SMTP needed)
- ✅ **Email Notifications** - Toggle notification preferences
- ✅ **Dark Mode** - UI theme preference
- ✅ **Per-User Settings** - Each user has their own settings
- ✅ **Auto-Created** - Settings created automatically on first access

## 📊 Database Schema

```sql
CREATE TABLE user_settings (
  id UUID PRIMARY KEY,
  user_id TEXT UNIQUE REFERENCES user(id) ON DELETE CASCADE,
  developer_mode BOOLEAN DEFAULT FALSE,
  email_notifications BOOLEAN DEFAULT TRUE,
  dark_mode BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);
```

## 🚀 Setup

### Step 1: Run Migration

```bash
cd backend
npm run db:push
```

This creates the `user_settings` table.

### Step 2: Restart Server

```bash
npm run dev
```

## 📡 API Endpoints

### 1. Get User Settings

```http
GET /api/settings
Authorization: Bearer <session_token>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "...",
    "userId": "usr_...",
    "developerMode": false,
    "emailNotifications": true,
    "darkMode": false,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

### 2. Update Settings

```http
PATCH /api/settings
Authorization: Bearer <session_token>
Content-Type: application/json

{
  "developerMode": true,
  "darkMode": true,
  "emailNotifications": false
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "...",
    "userId": "usr_...",
    "developerMode": true,
    "emailNotifications": false,
    "darkMode": true,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

### 3. Get Developer Mode Status

```http
GET /api/settings/developer-mode
Authorization: Bearer <session_token>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "developerMode": true
  }
}
```

### 4. Toggle Developer Mode

```http
POST /api/settings/developer-mode
Authorization: Bearer <session_token>
Content-Type: application/json

{
  "enabled": true
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "message": "Developer mode enabled",
    "settings": {
      "id": "...",
      "userId": "usr_...",
      "developerMode": true,
      "emailNotifications": true,
      "darkMode": false,
      "createdAt": "...",
      "updatedAt": "..."
    }
  }
}
```

## 🧪 Testing with Postman

### Complete Flow

#### 1. Register User
```http
POST http://localhost:3000/api/auth/sign-up/email
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "SecurePass123!",
  "name": "Test User"
}
```

#### 2. Login (save session cookie)
```http
POST http://localhost:3000/api/auth/sign-in/email
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "SecurePass123!"
}
```

#### 3. Get Settings (creates default settings)
```http
GET http://localhost:3000/api/settings
Cookie: <session_cookie>
```

#### 4. Enable Developer Mode
```http
POST http://localhost:3000/api/settings/developer-mode
Cookie: <session_cookie>
Content-Type: application/json

{
  "enabled": true
}
```

#### 5. Verify It's Enabled
```http
GET http://localhost:3000/api/settings/developer-mode
Cookie: <session_cookie>
```

## 💡 Developer Mode Benefits

When developer mode is enabled:

1. ✅ **No Email Verification Required**
   - Users are automatically verified
   - Skip SMTP configuration during development

2. ✅ **Faster Testing**
   - Register and login immediately
   - No waiting for verification emails

3. ✅ **Easy Development**
   - Test auth flows quickly
   - Focus on building features

## 🔧 Usage Examples

### Enable Developer Mode for All New Users (Optional)

If you want new users to have developer mode enabled by default:

Edit `backend/src/services/settings.service.ts`:

```typescript
async createDefaultSettings(userId: string) {
  const [settings] = await db
    .insert(userSettings)
    .values({
      userId,
      developerMode: true,  // ✅ Changed from false
      emailNotifications: true,
      darkMode: false,
    })
    .returning();

  return settings;
}
```

### Check Developer Mode in Code

```typescript
import { settingsService } from './services/settings.service.js';

const isDev = await settingsService.isDeveloperMode(userId);

if (isDev) {
  // Skip email verification
  // Enable debug features
  // Allow test data
}
```

## 🎯 Common Use Cases

### Use Case 1: Development Testing

```bash
# 1. Register
POST /api/auth/sign-up/email

# 2. Enable developer mode
POST /api/settings/developer-mode
Body: {"enabled": true}

# 3. Test without email verification
# All users are now auto-verified
```

### Use Case 2: Production Deployment

```bash
# 1. Set email verification in Better Auth config
requireEmailVerification: true

# 2. Configure SMTP settings in .env
SMTP_HOST=smtp.gmail.com
SMTP_USER=...
SMTP_PASSWORD=...

# 3. Developer mode becomes optional feature
# Users can still enable it for convenience
```

### Use Case 3: Per-User Testing

```bash
# Enable developer mode for specific test user
POST /api/settings/developer-mode
Body: {"enabled": true}

# Regular users don't have developer mode
# Test users can bypass verification
```

## 📋 Settings Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | UUID | Auto | Primary key |
| `userId` | TEXT | Required | Foreign key to user |
| `developerMode` | BOOLEAN | `false` | Auto-verify emails |
| `emailNotifications` | BOOLEAN | `true` | Send email notifications |
| `darkMode` | BOOLEAN | `false` | UI theme preference |
| `createdAt` | TIMESTAMP | NOW | Creation time |
| `updatedAt` | TIMESTAMP | NOW | Last update time |

## 🔍 Troubleshooting

### Issue 1: Settings Not Found

**Symptom:** 404 error when accessing `/api/settings`

**Solution:** Settings are created automatically on first access. Make sure:
- User is authenticated
- Session cookie is included
- Database migration ran successfully

### Issue 2: Table Doesn't Exist

**Error:** `relation "user_settings" does not exist`

**Solution:**
```bash
npm run db:push
```

### Issue 3: Developer Mode Not Working

**Check:**
1. Settings were updated: `GET /api/settings`
2. Developer mode is true: `GET /api/settings/developer-mode`
3. Server restarted after changing config

## 🚀 Next Steps

1. ✅ Run migration: `npm run db:push`
2. ✅ Restart server: `npm run dev`
3. ✅ Register a user
4. ✅ Login
5. ✅ Enable developer mode
6. ✅ Test without email verification!

## 📞 API Quick Reference

```bash
# Get settings
GET /api/settings

# Update settings
PATCH /api/settings
Body: {"developerMode": true, "darkMode": true}

# Get developer mode status
GET /api/settings/developer-mode

# Enable developer mode
POST /api/settings/developer-mode
Body: {"enabled": true}

# Disable developer mode
POST /api/settings/developer-mode
Body: {"enabled": false}
```

---

**Status:** Settings system ready! Run migrations and test. 🎉
