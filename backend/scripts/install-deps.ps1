# install-deps.ps1
# Installs all backend dependencies automatically so they don't need to be
# hand-added to package.json. Run:  npm run install:deps   (or)   ./install-deps.ps1
$ErrorActionPreference = "Stop"

# Runtime dependencies
$deps = @(
    "better-auth@^1",
    "bcryptjs@^3",
    "compression@^1.8",
    "cookie-parser@^1.4",
    "cors@^2.8",
    "dotenv@^17",
    "drizzle-orm@^0.45",
    "express@^5",
    "express-rate-limit@^8",
    "helmet@^8",
    "postgres@^3.4",
    "uuid@^11",
    "winston@^3.17",
    "zod@^4"
)

# Development dependencies
$devDeps = @(
    "@types/bcryptjs@^2.4",
    "@types/compression@^1.8",
    "@types/cookie-parser@^1.4",
    "@types/cors@^2.8",
    "@types/express@^5",
    "@types/node@^24",
    "@types/uuid@^10",
    "@typescript-eslint/eslint-plugin@^8",
    "@typescript-eslint/parser@^8",
    "drizzle-kit@^0.31",
    "eslint@^9",
    "prettier@^3",
    "tsx@^4",
    "typescript@^5.9",
    "vitest@^3"
)

Write-Host "Installing runtime dependencies..." -ForegroundColor Cyan
npm install --save --legacy-peer-deps @deps

Write-Host "Installing dev dependencies..." -ForegroundColor Cyan
npm install --save-dev --legacy-peer-deps @devDeps

Write-Host "All dependencies installed." -ForegroundColor Green
