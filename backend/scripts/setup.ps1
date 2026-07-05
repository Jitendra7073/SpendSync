# SpendSync Backend Setup Script (PowerShell)
# Run with: .\setup.ps1

Write-Host "🚀 SpendSync Backend Setup" -ForegroundColor Cyan
Write-Host "==========================" -ForegroundColor Cyan
Write-Host ""

# Check Node.js
Write-Host "Checking Node.js installation..." -ForegroundColor Yellow
try {
    $nodeVersion = node --version
    Write-Host "✅ Node.js version: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Node.js is not installed. Please install Node.js 20+" -ForegroundColor Red
    exit 1
}

# Check npm
try {
    $npmVersion = npm --version
    Write-Host "✅ npm version: $npmVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ npm is not installed" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Install dependencies
Write-Host "📦 Installing dependencies..." -ForegroundColor Yellow
npm install

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to install dependencies" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Dependencies installed successfully" -ForegroundColor Green
Write-Host ""

# Check and create .env
if (-not (Test-Path .env)) {
    Write-Host "📝 Creating .env file from .env.example..." -ForegroundColor Yellow
    Copy-Item .env.example .env
    Write-Host "✅ .env file created" -ForegroundColor Green
    Write-Host ""
    Write-Host "⚠️  IMPORTANT: Please edit .env file and add:" -ForegroundColor Yellow
    Write-Host "   - DATABASE_URL (PostgreSQL connection string)" -ForegroundColor White
    Write-Host "   - AUTH_SECRET (at least 32 characters)" -ForegroundColor White
    Write-Host ""
    Write-Host "   Example:" -ForegroundColor White
    Write-Host "   DATABASE_URL=postgresql://user:password@localhost:5432/spendsync" -ForegroundColor Gray
    
    # Generate a random secret
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $secret = [Convert]::ToBase64String($bytes)
    Write-Host "   AUTH_SECRET=$secret" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "✅ .env file already exists" -ForegroundColor Green
    Write-Host ""
}

# Create logs directory
if (-not (Test-Path logs)) {
    Write-Host "📁 Creating logs directory..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path logs | Out-Null
    Write-Host "✅ Logs directory created" -ForegroundColor Green
}

Write-Host ""
Write-Host "🎉 Setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Next steps:" -ForegroundColor Cyan
Write-Host "   1. Set up PostgreSQL database" -ForegroundColor White
Write-Host "   2. Update .env with your configuration" -ForegroundColor White
Write-Host "   3. Run: npm run db:generate" -ForegroundColor White
Write-Host "   4. Run: npm run db:push" -ForegroundColor White
Write-Host "   5. Run: npm run dev" -ForegroundColor White
Write-Host ""
Write-Host "📚 Documentation:" -ForegroundColor Cyan
Write-Host "   - README.md - Main documentation" -ForegroundColor White
Write-Host "   - API_TESTING.md - API testing guide" -ForegroundColor White
Write-Host "   - PROJECT_STRUCTURE.md - Architecture overview" -ForegroundColor White
Write-Host ""
