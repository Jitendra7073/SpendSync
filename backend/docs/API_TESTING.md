# SpendSync API Testing Guide

## Quick Test with cURL

### 1. Health Check
```bash
curl http://localhost:3000/health
```

### 2. Register a New User
```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "name": "John Doe"
  }'
```

### 3. Login
```bash
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }' \
  -c cookies.txt
```

### 4. Create a Category Rule
```bash
curl -X POST http://localhost:3000/api/categories \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "keyword": "swiggy",
    "category": "Food"
  }'
```

### 5. Create a Transaction
```bash
curl -X POST http://localhost:3000/api/transactions \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "amount": 450.50,
    "type": "debit",
    "merchant": "Swiggy",
    "category": "Food",
    "sourceApp": "GPay",
    "note": "Dinner order"
  }'
```

### 6. Get All Transactions
```bash
curl -X GET "http://localhost:3000/api/transactions?page=1&limit=10" \
  -H "Content-Type: application/json" \
  -b cookies.txt
```

### 7. Create a Budget
```bash
curl -X POST http://localhost:3000/api/budgets \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "category": "Food",
    "month": "2026-07",
    "limitAmount": 6000
  }'
```

### 8. Get Dashboard Summary
```bash
curl -X GET "http://localhost:3000/api/dashboard/summary?month=2026-07" \
  -H "Content-Type: application/json" \
  -b cookies.txt
```

### 9. Get Category Suggestion
```bash
curl -X POST http://localhost:3000/api/categories/suggest \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "merchant": "Swiggy Food Delivery"
  }'
```

### 10. Get Monthly Trend
```bash
curl -X GET "http://localhost:3000/api/dashboard/trend?months=6" \
  -H "Content-Type: application/json" \
  -b cookies.txt
```

## Sample Payloads

### Create Transaction (Debit)
```json
{
  "amount": 1250.00,
  "type": "debit",
  "merchant": "Amazon",
  "category": "Shopping",
  "sourceApp": "PhonePe",
  "note": "Electronics purchase"
}
```

### Create Transaction (Credit)
```json
{
  "amount": 50000.00,
  "type": "credit",
  "merchant": "Salary",
  "category": "Income",
  "note": "Monthly salary"
}
```

### Update Transaction
```json
{
  "category": "Entertainment",
  "note": "Movie tickets updated"
}
```

### Create Multiple Category Rules
```json
[
  { "keyword": "uber", "category": "Transport" },
  { "keyword": "ola", "category": "Transport" },
  { "keyword": "amazon", "category": "Shopping" },
  { "keyword": "flipkart", "category": "Shopping" },
  { "keyword": "zomato", "category": "Food" },
  { "keyword": "swiggy", "category": "Food" },
  { "keyword": "netflix", "category": "Entertainment" },
  { "keyword": "spotify", "category": "Entertainment" }
]
```

## Expected Response Formats

### Successful Transaction Creation
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "usr_abc123",
    "amount": "450.50",
    "type": "debit",
    "merchant": "Swiggy",
    "category": "Food",
    "sourceApp": "GPay",
    "note": "Dinner order",
    "createdAt": "2026-07-04T10:30:00.000Z",
    "updatedAt": "2026-07-04T10:30:00.000Z"
  }
}
```

### Dashboard Summary Response
```json
{
  "success": true,
  "data": {
    "month": "2026-07",
    "totals": {
      "totalSpent": 15250.00,
      "totalEarned": 50000.00,
      "totalTransactions": 45,
      "totalBudget": 30000.00,
      "netAmount": 34750.00
    },
    "categoryBreakdown": [
      {
        "category": "Food",
        "spent": 5200.00,
        "earned": 0,
        "transactionCount": 18,
        "budget": 6000.00,
        "remaining": 800.00,
        "percentageUsed": 86.67
      }
    ],
    "monthlyTrend": [...]
  }
}
```

### Error Response Example
```json
{
  "success": false,
  "error": {
    "message": "Transaction not found",
    "code": "NOT_FOUND"
  }
}
```

## HTTP Status Codes

- `200` - Success
- `201` - Created
- `204` - No Content (successful deletion)
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `409` - Conflict
- `422` - Validation Error
- `429` - Too Many Requests (rate limit)
- `500` - Internal Server Error

## Rate Limits

- General API: 100 requests per 15 minutes
- Auth endpoints: 5 attempts per 15 minutes

## Notes

- All authenticated requests must include session cookie or Bearer token
- Timestamps are in ISO 8601 format (UTC)
- Amounts are returned as strings to preserve precision
- All queries are automatically filtered by the authenticated user's ID
- Pagination defaults: page=1, limit=50
