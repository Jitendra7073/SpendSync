# 🔧 Troubleshooting Guide

## Issues Fixed

### ✅ Better Auth Module Not Found
**Error**: `Cannot find package '@better-auth/drizzle'`

**Solution**: Updated to use correct Better Auth package structure:
- Changed from `@better-auth/core` to `better-auth`
- Changed from `@better-auth/drizzle` to `better-auth/adapters/drizzle`
- Updated database schema to use camelCase naming (Better Auth standard)
- Fixed all imports and references

## Current Setup Status

### ✅ Completed
- [x] Fixed Better Auth imports
- [x] Updated database schema to match Better Auth expectations
- [x] Fixed authentication middleware
- [x] Updated server.ts to handle Better Auth routes correctly
- [x] Reinstalled dependencies with correct versions
- [x] Created .env file with default values

### ⚠️ Required Before Running

1. **PostgreSQL Database**
   - Install PostgreSQL if not already installed
   - Create database: `CREATE DATABASE spendsync;`
   - Update `DATABASE_URL` in `.env` file with your credentials

2. **Auth Secret**
   - Generate a secure random secret (32+ characters)
   - Update `AUTH_SECRET` in `.env` file

## How to Run

### Step 1: Set up PostgreSQL

**Option A: Local PostgreSQL**
```bash
# Install PostgreSQL (if not installed)
# Windows: Download from https://www.postgresql.org/download/windows/

# Create database
psql -U postgres
CREATE DATABASE spendsync;
\q
```

**Option B: Use Docker**
```bash
docker run --name spendsync-postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=spendsync \
  -p 5432:5432 \
  -d postgres:14
```

**Option C: Use Online Service (Easiest)**
- Neon: https://neon.tech (Free tier available)
- Supabase: https://supabase.com (Free tier available)
- ElephantSQL: https://www.elephantsql.com (Free tier available)

### Step 2: Update .env

Edit `backend/.env`:
```env
# Update with your database URL
DATABASE_URL=postgresql://username:password@host:port/database

# Generate a secure secret
AUTH_SECRET=$(openssl rand -base64 32)
# Or use any 32+ character random string
```

### Step 3: Run Migrations

```bash
cd backend
npm run db:push
```

### Step 4: Start Server

```bash
npm run dev
```

Server should start at `http://localhost:3000`

## Quick Test

Once server is running:

```bash
# Health check
curl http://localhost:3000/health

# Should return:
# {"status":"ok","timestamp":"...","environment":"development"}
```

## Common Issues

### Issue 1: Database Connection Failed
```
Error: connect ECONNREFUSED 127.0.0.1:5432
```
**Solution**: 
- Make sure PostgreSQL is running
- Check DATABASE_URL is correct
- Verify database exists

### Issue 2: AUTH_SECRET Error
```
Invalid environment variables: AUTH_SECRET must be at least 32 characters
```
**Solution**: 
- Generate a longer secret
- Use: `openssl rand -base64 32` or any random 32+ char string

### Issue 3: Port Already in Use
```
Error: listen EADDRINUSE: address already in use :::3000
```
**Solution**: 
- Change PORT in .env to 3001, 3002, etc.
- Or kill the process using port 3000

### Issue 4: Module Not Found
```
Error [ERR_MODULE_NOT_FOUND]: Cannot find package 'xxx'
```
**Solution**: 
```bash
# Clean install
rm -rf node_modules package-lock.json
npm install
```

## Verification Steps

After server starts successfully:

1. ✅ Health check works
2. ✅ No errors in console
3. ✅ Database tables created (check with `npm run db:studio`)
4. ✅ Can register a user
5. ✅ Can login

## Testing the API

### 1. Register a User
```bash
curl -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!",
    "name": "Test User"
  }'
```

### 2. Login
```bash
curl -X POST http://localhost:3000/api/auth/sign-in/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
  }' \
  -c cookies.txt
```

### 3. Test Authenticated Endpoint
```bash
curl http://localhost:3000/api/transactions \
  -b cookies.txt
```

## Getting Help

If issues persist:

1. Check logs in `backend/logs/` directory
2. Review error messages carefully
3. Verify all environment variables are set
4. Ensure PostgreSQL is accessible
5. Try with a fresh database

## Useful Commands

```bash
# View logs
cat logs/error.log
cat logs/combined.log

# Check database
npm run db:studio

# Clean and rebuild
rm -rf node_modules dist
npm install
npm run build

# Test database connection
psql $DATABASE_URL -c "SELECT version();"
```

## Next Steps

Once server is running successfully:

1. ✅ Test all auth endpoints
2. ✅ Create some test transactions
3. ✅ Verify dashboard endpoints work
4. ✅ Test with the Android app

---

**Status**: Server should now start without the Better Auth module error!
