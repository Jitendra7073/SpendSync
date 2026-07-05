# ✅ SpendSync Backend Setup Checklist

## Pre-requisites
- [ ] Node.js 20+ installed (`node --version`)
- [ ] npm 10+ installed (`npm --version`)
- [ ] PostgreSQL 14+ installed OR access to online PostgreSQL service

## Setup Steps

### 1. PostgreSQL Database

Choose one option:

**Option A: Local PostgreSQL** ⭐ Recommended for development
```bash
# Windows: Download installer from postgresql.org
# After installation:
psql -U postgres
CREATE DATABASE spendsync;
\q
```

**Option B: Docker** 🐳 Quick start
```bash
docker run --name spendsync-postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=spendsync \
  -p 5432:5432 \
  -d postgres:14
```

**Option C: Online Service** ☁️ No local setup needed
- Neon: https://neon.tech (Free)
- Supabase: https://supabase.com (Free)
- Railway: https://railway.app (Free trial)

- [ ] Database created and accessible

### 2. Environment Configuration

Edit `backend/.env`:

```env
# 1. Update DATABASE_URL with your connection string
DATABASE_URL=postgresql://username:password@host:5432/spendsync

# 2. Generate a secure AUTH_SECRET (32+ characters)
# Use: https://generate-secret.vercel.app/32
AUTH_SECRET=your-generated-secret-here

# 3. Other settings (optional for now)
PORT=3000
NODE_ENV=development
```

- [ ] DATABASE_URL updated
- [ ] AUTH_SECRET generated and set

### 3. Install Dependencies

```bash
cd backend
npm install
```

- [ ] Dependencies installed (should see "added 372 packages")

### 4. Run Database Migrations

```bash
npm run db:push
```

Expected output:
```
✓ Generated schema
✓ Pushed to database
```

- [ ] Migrations completed successfully
- [ ] Tables created in database

### 5. Start Development Server

```bash
npm run dev
```

Expected output:
```
🚀 Server started successfully
📦 Environment: development
🌐 API URL: http://localhost:3000
```

- [ ] Server starts without errors
- [ ] No "module not found" errors
- [ ] Listening on port 3000

## Verification Tests

### Test 1: Health Check
```bash
curl http://localhost:3000/health
```

Expected:
```json
{
  "status": "ok",
  "timestamp": "2026-07-04T...",
  "environment": "development"
}
```

- [ ] Health check passes

### Test 2: Database Studio (Optional)
```bash
npm run db:studio
```

Opens at `https://local.drizzle.studio`

- [ ] Can view database tables
- [ ] See user, session, transactions, categories, budgets tables

### Test 3: Register User
```bash
curl -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!",
    "name": "Test User"
  }'
```

- [ ] User registration works

### Test 4: Login
```bash
curl -X POST http://localhost:3000/api/auth/sign-in/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePass123!"
  }' \
  -c cookies.txt -v
```

- [ ] Login successful
- [ ] Session cookie received

### Test 5: Protected Endpoint
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

- [ ] Protected endpoint accessible with session

## Common Issues

### ❌ Database Connection Failed
```
Error: connect ECONNREFUSED 127.0.0.1:5432
```

**Fix:**
- Ensure PostgreSQL is running
- Check DATABASE_URL is correct
- Test with: `psql $DATABASE_URL -c "SELECT 1;"`

### ❌ AUTH_SECRET Too Short
```
Invalid environment variables: AUTH_SECRET must be at least 32 characters
```

**Fix:**
- Generate longer secret: https://generate-secret.vercel.app/32
- Or use: `openssl rand -base64 32`

### ❌ Port In Use
```
Error: listen EADDRINUSE :::3000
```

**Fix:**
- Change PORT in .env to 3001
- Or kill process: `netstat -ano | findstr :3000`

### ❌ Module Not Found
```
Cannot find module 'xxx'
```

**Fix:**
```bash
rm -rf node_modules package-lock.json
npm install
```

## Success Indicators

You're ready to go when:

- ✅ Server starts without errors
- ✅ Health check returns OK
- ✅ Can register and login users
- ✅ Protected endpoints work with authentication
- ✅ Database tables exist and are queryable
- ✅ Logs show no errors

## Next Steps

Once everything is working:

1. **Test all endpoints** - See `API_TESTING.md`
2. **Connect Android app** - Update API_URL in app
3. **Create sample data** - Add test transactions
4. **Explore API** - Try different endpoints
5. **Read documentation** - Check `README.md` for full API reference

## Quick Reference

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start dev server |
| `npm run db:push` | Run migrations |
| `npm run db:studio` | Database GUI |
| `npm run lint` | Check code |
| `npm run build` | Build for production |

## Need Help?

1. Check `TROUBLESHOOTING.md` for detailed solutions
2. Review `FIX_SUMMARY.md` for recent fixes
3. See `README.md` for full documentation
4. Check logs in `logs/error.log`

---

**Status**: Ready to start! Follow the checklist above. ✨
