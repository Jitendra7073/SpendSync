# SpendSync Backend API

A comprehensive expense tracker backend built with Node.js, Express, Better Auth, and PostgreSQL.

## 🏗️ Architecture

```
backend/
├── src/
│   ├── config/          # Configuration files (env, auth)
│   ├── db/              # Database setup and schemas
│   │   └── schema/      # Drizzle ORM schemas
│   ├── controllers/     # Request handlers
│   ├── services/        # Business logic layer
│   ├── routes/          # API route definitions
│   ├── middleware/      # Express middleware
│   ├── types/           # TypeScript types and Zod schemas
│   ├── utils/           # Utility functions
│   └── server.ts        # Main application entry point
├── drizzle/             # Generated migrations (auto-created)
├── logs/                # Application logs (auto-created)
└── package.json
```

## 🚀 Quick Start

### Prerequisites

- Node.js >= 20.0.0
- PostgreSQL >= 14
- npm >= 10.0.0

### Installation

1. **Install dependencies**
   ```bash
   cd backend
   npm install
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Set up PostgreSQL database**
   ```bash
   # Create database
   createdb spendsync
   
   # Or using psql
   psql -U postgres
   CREATE DATABASE spendsync;
   ```

4. **Generate and run migrations**
   ```bash
   npm run db:generate
   npm run db:push
   ```

5. **Start development server**
   ```bash
   npm run dev
   ```

The server will start at `http://localhost:3000`

## 📝 Environment Variables

Required environment variables (see `.env.example`):

- `DATABASE_URL` - PostgreSQL connection string
- `AUTH_SECRET` - Secret key for Better Auth (min 32 characters)
- `PORT` - Server port (default: 3000)
- `NODE_ENV` - Environment (development/production)

## 🔐 Authentication

The API uses **Better Auth** for authentication with email/password:

### Auth Endpoints

- `POST /api/auth/register` - Create new account
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout
- `POST /api/auth/forgot-password` - Request password reset
- `POST /api/auth/reset-password` - Reset password
- `GET /api/auth/session` - Get current session

### Protected Routes

All API routes (except auth) require authentication. Include the session token in:
- Cookie: `better-auth.session_token`
- Header: `Authorization: Bearer <token>`

## 📡 API Endpoints

### Transactions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/transactions` | Create transaction |
| GET | `/api/transactions` | List transactions (paginated) |
| GET | `/api/transactions/:id` | Get transaction by ID |
| PATCH | `/api/transactions/:id` | Update transaction |
| DELETE | `/api/transactions/:id` | Delete transaction |

**Query Parameters** (GET /api/transactions):
- `startDate` - Filter by start date (ISO 8601)
- `endDate` - Filter by end date (ISO 8601)
- `category` - Filter by category
- `type` - Filter by type (debit/credit)
- `page` - Page number (default: 1)
- `limit` - Items per page (default: 50)

### Categories

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/categories` | Create category rule |
| GET | `/api/categories` | List all category rules |
| GET | `/api/categories/:id` | Get category by ID |
| PATCH | `/api/categories/:id` | Update category rule |
| DELETE | `/api/categories/:id` | Delete category rule |
| POST | `/api/categories/suggest` | Get category suggestion |

### Budgets

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/budgets` | Create budget |
| GET | `/api/budgets` | List budgets |
| GET | `/api/budgets/:id` | Get budget by ID |
| PATCH | `/api/budgets/:id` | Update budget |
| DELETE | `/api/budgets/:id` | Delete budget |

**Query Parameters** (GET /api/budgets):
- `month` - Filter by month (YYYY-MM)
- `category` - Filter by category

### Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard/summary` | Get monthly summary |
| GET | `/api/dashboard/trend` | Get monthly trend |
| GET | `/api/dashboard/top-merchants` | Get top merchants |

**Query Parameters**:
- `/summary?month=YYYY-MM` - Month to analyze (default: current)
- `/trend?months=6` - Number of months (default: 6)
- `/top-merchants?limit=10` - Number of merchants (default: 10)

## 📊 Database Schema

### Tables

1. **users** - User accounts (managed by Better Auth)
2. **sessions** - Active sessions (managed by Better Auth)
3. **accounts** - Auth provider info (managed by Better Auth)
4. **verifications** - Email verification tokens (managed by Better Auth)
5. **transactions** - User transactions
6. **categories** - Category keyword rules
7. **budgets** - Monthly budget limits

## 🛠️ Development

### Available Scripts

```bash
npm run dev          # Start development server with hot reload
npm run build        # Build for production
npm run start        # Start production server
npm run lint         # Run ESLint
npm run format       # Format code with Prettier
npm run db:generate  # Generate database migrations
npm run db:push      # Push schema to database
npm run db:studio    # Open Drizzle Studio (database GUI)
npm test            # Run tests
```

### Database Management

```bash
# Generate new migration after schema changes
npm run db:generate

# Apply migrations to database
npm run db:push

# Open database GUI
npm run db:studio
```

## 🔒 Security Features

- ✅ HTTPS-only cookies (production)
- ✅ Helmet.js security headers
- ✅ CORS protection
- ✅ Rate limiting (general + auth-specific)
- ✅ Input validation with Zod
- ✅ SQL injection protection (Drizzle ORM)
- ✅ Password hashing (Better Auth)
- ✅ Session management
- ✅ Request size limits
- ✅ Row-level security (all queries filtered by user ID)

## 📦 Tech Stack

- **Runtime**: Node.js 20+
- **Framework**: Express.js
- **Language**: TypeScript
- **Database**: PostgreSQL
- **ORM**: Drizzle ORM
- **Authentication**: Better Auth
- **Validation**: Zod
- **Logging**: Winston
- **Security**: Helmet, CORS, Rate Limiting

## 🚢 Production Deployment

### Build

```bash
npm run build
```

### Environment

Set `NODE_ENV=production` and ensure all production environment variables are set.

### Recommended Hosting

- **Backend**: Railway, Render, Fly.io, AWS EC2
- **Database**: Neon, Supabase, AWS RDS, Railway PostgreSQL

### Database Backup

Enable automated backups on your PostgreSQL provider. Keep encrypted backups separate from your hosting provider.

## 📄 API Response Format

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "page": 1,
    "limit": 50,
    "total": 100
  }
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "message": "Error description",
    "code": "ERROR_CODE",
    "details": { ... }
  }
}
```

## 🧪 Testing

```bash
npm test
```

## 📝 License

MIT

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request
