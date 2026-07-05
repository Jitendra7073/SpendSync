# Better Auth Test Commands

## 1. Health Check
curl http://localhost:3000/health

## 2. Register a New User
curl -X POST http://localhost:3000/api/auth/sign-up/email \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\",\"password\":\"SecurePass123!\",\"name\":\"Test User\"}"

## 3. Login
curl -X POST http://localhost:3000/api/auth/sign-in/email \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\",\"password\":\"SecurePass123!\"}" \
  -c cookies.txt -v

## 4. Get Session
curl http://localhost:3000/api/auth/session \
  -b cookies.txt

## 5. Test Protected Endpoint (Transactions)
curl http://localhost:3000/api/transactions \
  -b cookies.txt

## 6. Logout
curl -X POST http://localhost:3000/api/auth/sign-out \
  -b cookies.txt

---

## PowerShell Commands (Windows)

### Register
Invoke-WebRequest -Uri "http://localhost:3000/api/auth/sign-up/email" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body '{"email":"test@example.com","password":"SecurePass123!","name":"Test User"}'

### Login
Invoke-WebRequest -Uri "http://localhost:3000/api/auth/sign-in/email" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body '{"email":"test@example.com","password":"SecurePass123!"}' `
  -SessionVariable session

### Get Transactions (with session)
Invoke-WebRequest -Uri "http://localhost:3000/api/transactions" `
  -WebSession $session
