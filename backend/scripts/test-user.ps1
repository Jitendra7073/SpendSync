# Test User Registration and Cleanup Script
# This script helps debug registration issues

Write-Host "🔧 SpendSync User Management Tool" -ForegroundColor Cyan
Write-Host "=" * 50

# Check if server is running
$response = $null
try {
    $response = Invoke-WebRequest -Uri "http://localhost:3000/health" -Method GET -ErrorAction Stop
    Write-Host "✅ Server is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Server is not running. Start it with: npm run dev" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Choose an option:" -ForegroundColor Yellow
Write-Host "1. Register a new user"
Write-Host "2. Login with existing user"
Write-Host "3. Test if user exists (via login attempt)"
Write-Host "4. Clean database (requires db:studio or psql)"
Write-Host ""

$choice = Read-Host "Enter choice (1-4)"

switch ($choice) {
    "1" {
        Write-Host "`n📝 Registering new user..." -ForegroundColor Cyan
        $email = Read-Host "Email"
        $password = Read-Host "Password" -AsSecureString
        $passwordText = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))
        $name = Read-Host "Name"
        
        $body = @{
            email = $email
            password = $passwordText
            name = $name
        } | ConvertTo-Json
        
        try {
            $response = Invoke-WebRequest `
                -Uri "http://localhost:3000/api/auth/sign-up/email" `
                -Method POST `
                -Headers @{"Content-Type"="application/json"} `
                -Body $body `
                -ErrorAction Stop
            
            Write-Host "✅ Registration successful!" -ForegroundColor Green
            Write-Host "Response:" -ForegroundColor White
            $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
        } catch {
            Write-Host "❌ Registration failed!" -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            if ($_.Exception.Response) {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseText = $reader.ReadToEnd()
                Write-Host "Server response: $responseText" -ForegroundColor Yellow
            }
        }
    }
    
    "2" {
        Write-Host "`n🔐 Logging in..." -ForegroundColor Cyan
        $email = Read-Host "Email"
        $password = Read-Host "Password" -AsSecureString
        $passwordText = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))
        
        $body = @{
            email = $email
            password = $passwordText
        } | ConvertTo-Json
        
        try {
            $response = Invoke-WebRequest `
                -Uri "http://localhost:3000/api/auth/sign-in/email" `
                -Method POST `
                -Headers @{"Content-Type"="application/json"} `
                -Body $body `
                -SessionVariable session `
                -ErrorAction Stop
            
            Write-Host "✅ Login successful!" -ForegroundColor Green
            Write-Host "Response:" -ForegroundColor White
            $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
        } catch {
            Write-Host "❌ Login failed!" -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            if ($_.Exception.Response) {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseText = $reader.ReadToEnd()
                Write-Host "Server response: $responseText" -ForegroundColor Yellow
            }
        }
    }
    
    "3" {
        Write-Host "`n🔍 Testing user existence..." -ForegroundColor Cyan
        $email = Read-Host "Email to test"
        
        Write-Host "Attempting login (will fail if user exists with wrong password)..." -ForegroundColor Gray
        $body = @{
            email = $email
            password = "wrong-password-test"
        } | ConvertTo-Json
        
        try {
            $response = Invoke-WebRequest `
                -Uri "http://localhost:3000/api/auth/sign-in/email" `
                -Method POST `
                -Headers @{"Content-Type"="application/json"} `
                -Body $body `
                -ErrorAction Stop
        } catch {
            if ($_.Exception.Response.StatusCode -eq 401) {
                Write-Host "✅ User EXISTS (invalid password)" -ForegroundColor Green
            } else {
                Write-Host "❓ Unknown status: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
            }
            
            if ($_.Exception.Response) {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseText = $reader.ReadToEnd()
                Write-Host "Response: $responseText" -ForegroundColor Gray
            }
        }
    }
    
    "4" {
        Write-Host "`n🗑️  Database Cleanup" -ForegroundColor Cyan
        Write-Host "To clean the database, you have these options:" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Option 1: Using Drizzle Studio (GUI)"
        Write-Host "  1. Run: npm run db:studio"
        Write-Host "  2. Open: https://local.drizzle.studio"
        Write-Host "  3. Navigate to 'user' table"
        Write-Host "  4. Delete the users you want to remove"
        Write-Host ""
        Write-Host "Option 2: Using psql (CLI)"
        Write-Host "  psql `$env:DATABASE_URL -c `"DELETE FROM session; DELETE FROM account; DELETE FROM user;`""
        Write-Host ""
        Write-Host "Option 3: Reset and recreate database"
        Write-Host "  npm run db:push -- --force"
        Write-Host ""
    }
    
    default {
        Write-Host "❌ Invalid choice" -ForegroundColor Red
    }
}

Write-Host "`n" -NoNewline
