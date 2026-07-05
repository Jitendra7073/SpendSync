# Test server startup
Write-Host "Testing server startup..." -ForegroundColor Cyan

# Check if .env exists
if (-not (Test-Path .env)) {
    Write-Host "Creating .env file..." -ForegroundColor Yellow
    Copy-Item .env.example .env
    Write-Host "⚠️  Please edit .env file with your DATABASE_URL and AUTH_SECRET" -ForegroundColor Yellow
    exit 1
}

# Try to start server
Write-Host "Starting development server..." -ForegroundColor Green
npm run dev
