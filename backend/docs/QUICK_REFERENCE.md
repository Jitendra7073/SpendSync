# 🚀 SpendSync Backend - Quick Reference Guide

## 📦 Installation (One-Time Setup)

```bash
cd backend
npm install
cp .env.example .env
# Edit .env with your configuration
npm run db:push
```

## 🎯 Common Commands

### Development
```bash
npm run dev              # Start dev server with hot reload
npm run db:studio        # Open database GUI
npm run lint             # Check code quality
npm run format           # Format code
```

### Database
```bash
npm run db:generate      # Generate migrations
npm run db:push          # Apply migrations
```

### Production
```bash
npm run build            # Build TypeScript
npm start                # Start production server
```

## 🔧 Environment Variables (Required)

```env
# Minimum required configuration
DATABASE_URL=postgresql://user:pass@localhost:5432/spendsync
AUTH_SECRET=your-32-character-secret-key-here
PORT=3000
NODE_ENV=development
```

## 📡 API Quick Reference

### Base URL
```
http://localhost:3000/api
```

### Authentication
```bash
# Register
POST /auth/register
Body: { "email": "user@email.com", "password": "pass123", "name": "Name" }

# Login
POST /auth/login
Body: { "email": "user@email.com", "password": "pass123" }
```

### Transactions
```bash
# Create
POST /transactions
Body: { "amount": 450, "type": "debit", "merchant": "Swiggy", "category": "Food" }

# List (with filters)
GET /transactions?startDate=2026-07-01&category=Food&page=1&limit=50

# Update
PATCH /transactions/:id
Body: { "category": "Dining" }

# Delete
DELETE /transactions/:id
```

### Categories
```bash
# Create rule
POST /categories
Body: { "keyword": "swiggy", "category": "Food" }

# Suggest category
POST /categories/suggest
Body: { "merchant": "Swiggy Delivery" }

# List all
GET /categories
```

### Budgets
```bash
# Create
POST /budgets
Body: { "category": "Food", "month": "2026-07", "limitAmount": 6000 }

# List
GET /budgets?month=2026-07
```

### Dashboard
```bash
# Monthly summary
GET /dashboard/summary?month=2026-07

# Trend (last 6 months)
GET /dashboard/trend?months=6

# Top merchants
GET /dashboard/top-merchants?limit=10
```

## 🔐 Authentication

All endpoints (except `/auth/*`) require authentication:

**Option 1: Cookie (Automatic)**
```bash
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@email.com","password":"pass"}' \
  -c cookies.txt

curl http://localhost:3000/api/transactions -b cookies.txt
```

**Option 2: Bearer Token**
```bash
# Get token from login response
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:3000/api/transactions
```

## 📁 Key Files

| File | Purpose |
|------|---------|
| `src/server.ts` | Main entry point |
| `src/config/env.ts` | Environment config |
| `src/config/auth.ts` | Better Auth setup |
| `src/db/schema/*.ts` | Database schemas |
| `src/routes/*.ts` | API endpoints |
| `src/controllers/*.ts` | Request handlers |
| `src/services/*.ts` | Business logic |
| `src/middleware/*.ts` | Auth, validation, errors |

## 🐛 Debugging

### Check Server Health
```bash
curl http://localhost:3000/health
```

### View Logs
```bash
# Real-time logs (development)
npm run dev

# Log files (production)
tail -f logs/combined.log
tail -f logs/error.log
```

### Database Issues
```bash
# Check connection
npm run db:studio

# Reset database (⚠️ deletes all data)
npm run db:push -- --force
```

## 🔍 Common Errors

### "Database connection failed"
- Check `DATABASE_URL` in `.env`
- Ensure PostgreSQL is running
- Verify database exists

### "Authentication failed"
- Check if session cookie is being sent
- Verify `AUTH_SECRET` is set
- Try logging in again

### "Validation error"
- Check request body format
- Ensure required fields are present
- Verify data types (amount as number, month as YYYY-MM)

## 📊 Database Schema Quick Reference

```
users
  ├─ id (PK)
  ├─ email (unique)
  └─ emailVerified

sessions
  ├─ id (PK)
  ├─ userId (FK → users)
  └─ expiresAt

transactions
  ├─ id (PK)
  ├─ userId (FK → users)
  ├─ amount (decimal)
  ├─ type (debit/credit)
  ├─ merchant
  ├─ category
  ├─ sourceApp
  └─ note

categories
  ├─ id (PK)
  ├─ userId (FK → users)
  ├─ keyword
  └─ category

budgets
  ├─ id (PK)
  ├─ userId (FK → users)
  ├─ category
  ├─ month (YYYY-MM)
  └─ limitAmount
```

## 🎨 Response Format

### Success
```json
{
  "success": true,
  "data": { ... },
  "meta": { "page": 1, "limit": 50, "total": 100 }
}
```

### Error
```json
{
  "success": false,
  "error": {
    "message": "Error message",
    "code": "ERROR_CODE",
    "details": { ... }
  }
}
```

## 🚀 Deployment Checklist

- [ ] Set `NODE_ENV=production`
- [ ] Use strong `AUTH_SECRET` (32+ chars)
- [ ] Configure production `DATABASE_URL`
- [ ] Set up SSL certificate (HTTPS)
- [ ] Configure `ALLOWED_ORIGINS` for CORS
- [ ] Enable database backups
- [ ] Set up monitoring/logging
- [ ] Configure SMTP for emails
- [ ] Test all endpoints
- [ ] Run `npm run build`

## 📞 Getting Help

1. **Documentation**: Check `README.md`
2. **API Testing**: See `API_TESTING.md`
3. **Architecture**: Read `PROJECT_STRUCTURE.md`
4. **Build Info**: Review `BUILD_SUMMARY.md`

## 💡 Pro Tips

- Use `npm run db:studio` to visually browse database
- Check `logs/error.log` for detailed error traces
- Use Postman/Insomnia for easier API testing
- Enable ESLint extension in your IDE
- Set up Git hooks with Husky (optional)

---

**Quick Start**: `npm install` → Edit `.env` → `npm run db:push` → `npm run dev`
