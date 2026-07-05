# SpendSync Backend - Complete Project Structure

```
backend/
│
├── package.json                    # Dependencies and scripts
├── tsconfig.json                   # TypeScript configuration
├── drizzle.config.ts              # Drizzle ORM configuration
├── .env.example                    # Environment variables template
├── .gitignore                      # Git ignore rules
├── .eslintrc.cjs                   # ESLint configuration
├── .prettierrc                     # Prettier configuration
├── README.md                       # Main documentation
├── API_TESTING.md                  # API testing guide
│
├── src/
│   ├── server.ts                   # Main application entry point
│   │
│   ├── config/
│   │   ├── env.ts                  # Environment configuration & validation
│   │   └── auth.ts                 # Better Auth configuration
│   │
│   ├── db/
│   │   ├── index.ts                # Database connection setup
│   │   ├── schema/
│   │   │   ├── index.ts            # Schema exports
│   │   │   ├── auth.schema.ts      # User, session, account tables
│   │   │   ├── transactions.schema.ts  # Transactions table
│   │   │   ├── categories.schema.ts    # Categories table
│   │   │   └── budgets.schema.ts       # Budgets table
│   │   └── migrations/             # Migration files (auto-generated)
│   │
│   ├── controllers/
│   │   ├── transaction.controller.ts   # Transaction HTTP handlers
│   │   ├── category.controller.ts      # Category HTTP handlers
│   │   ├── budget.controller.ts        # Budget HTTP handlers
│   │   └── dashboard.controller.ts     # Dashboard HTTP handlers
│   │
│   ├── services/
│   │   ├── transaction.service.ts      # Transaction business logic
│   │   ├── category.service.ts         # Category business logic
│   │   ├── budget.service.ts           # Budget business logic
│   │   └── dashboard.service.ts        # Analytics & aggregation logic
│   │
│   ├── routes/
│   │   ├── index.ts                    # Main router
│   │   ├── transaction.routes.ts       # Transaction endpoints
│   │   ├── category.routes.ts          # Category endpoints
│   │   ├── budget.routes.ts            # Budget endpoints
│   │   └── dashboard.routes.ts         # Dashboard endpoints
│   │
│   ├── middleware/
│   │   ├── auth.middleware.ts          # Authentication middleware
│   │   ├── error.middleware.ts         # Global error handler
│   │   ├── rateLimiter.middleware.ts   # Rate limiting
│   │   └── validate.middleware.ts      # Request validation
│   │
│   ├── types/
│   │   ├── transaction.types.ts        # Transaction types & schemas
│   │   ├── category.types.ts           # Category types & schemas
│   │   └── budget.types.ts             # Budget types & schemas
│   │
│   └── utils/
│       ├── logger.ts                   # Winston logger setup
│       ├── response.ts                 # API response helpers
│       └── errors.ts                   # Custom error classes
│
├── drizzle/                        # Generated migration files (auto-created)
├── logs/                           # Application logs (auto-created)
└── node_modules/                   # Dependencies (auto-created)
```

## 📁 Directory Descriptions

### `/src/config`
Application configuration including environment variables and auth setup.

### `/src/db`
Database connection, Drizzle ORM schemas, and migrations.

### `/src/controllers`
HTTP request handlers that receive requests, call services, and return responses.

### `/src/services`
Business logic layer containing core application functionality.

### `/src/routes`
Express route definitions mapping HTTP methods to controller functions.

### `/src/middleware`
Express middleware for authentication, validation, error handling, etc.

### `/src/types`
TypeScript types and Zod validation schemas for request/response data.

### `/src/utils`
Utility functions for logging, responses, and error handling.

## 🔄 Request Flow

```
HTTP Request
    ↓
Express Router (routes/)
    ↓
Middleware (auth, validation)
    ↓
Controller (controllers/)
    ↓
Service (services/)
    ↓
Database (Drizzle ORM)
    ↓
Service Response
    ↓
Controller Response
    ↓
HTTP Response
```

## 📊 Database Schema Overview

```
users (Better Auth)
├── id (PK)
├── email (unique)
├── emailVerified
├── name
└── timestamps

sessions (Better Auth)
├── id (PK)
├── userId (FK → users)
├── token
└── expiresAt

transactions
├── id (PK)
├── userId (FK → users)
├── amount
├── type (debit/credit)
├── merchant
├── category
├── sourceApp
├── note
└── timestamps

categories
├── id (PK)
├── userId (FK → users)
├── keyword
├── category
└── timestamps

budgets
├── id (PK)
├── userId (FK → users)
├── category
├── month (YYYY-MM)
├── limitAmount
└── timestamps
```

## 🛡️ Security Layers

1. **Network**: HTTPS, CORS, Rate Limiting
2. **Authentication**: Better Auth, Session Management
3. **Authorization**: User-scoped queries (row-level security)
4. **Input**: Zod validation, SQL injection protection
5. **Output**: Sanitized responses, error masking (production)

## 🚀 Key Features

✅ **TypeScript** - Full type safety
✅ **Better Auth** - Production-ready authentication
✅ **Drizzle ORM** - Type-safe database queries
✅ **Zod Validation** - Runtime type checking
✅ **Rate Limiting** - DDoS protection
✅ **Error Handling** - Centralized error management
✅ **Logging** - Winston logger with file rotation
✅ **CORS** - Configurable cross-origin access
✅ **Security Headers** - Helmet.js protection
✅ **Compression** - Response compression
✅ **Hot Reload** - Development with tsx watch

## 📦 Total Files Created

- **Configuration**: 7 files
- **Database**: 6 files
- **Services**: 4 files
- **Controllers**: 4 files
- **Routes**: 5 files
- **Middleware**: 4 files
- **Types**: 3 files
- **Utils**: 3 files
- **Documentation**: 3 files

**Total: 39+ files** in a well-organized, production-ready structure!
