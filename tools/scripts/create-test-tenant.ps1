# Script to create a test tenant with an admin user
# Usage: .\create-test-tenant.ps1

$API_BASE = "http://localhost:8081"

Write-Host "Creating test tenant..." -ForegroundColor Cyan

# Step 1: Create tenant
$createTenantBody = @{
    name = "Test Company Ltd"
    planCode = "PROFESSIONAL"
} | ConvertTo-Json

try {
    $tenantResponse = Invoke-RestMethod `
        -Uri "$API_BASE/tenants" `
        -Method POST `
        -Body $createTenantBody `
        -ContentType "application/json"
    
    $tenantId = $tenantResponse.tenantId
    Write-Host "✓ Tenant created successfully!" -ForegroundColor Green
    Write-Host "  Tenant ID: $tenantId" -ForegroundColor Gray
    Write-Host "  Name: $($tenantResponse.name)" -ForegroundColor Gray
    Write-Host "  Status: $($tenantResponse.status)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to create tenant: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Bootstrap admin user
Write-Host "`nCreating admin user..." -ForegroundColor Cyan

$bootstrapAdminBody = @{
    email = "admin@testcompany.com"
    password = "Admin@123456"
    firstName = "Admin"
    lastName = "User"
} | ConvertTo-Json

try {
    $adminResponse = Invoke-RestMethod `
        -Uri "$API_BASE/tenants/$tenantId/bootstrap-admin" `
        -Method POST `
        -Body $bootstrapAdminBody `
        -ContentType "application/json" `
        -Headers @{
            "X-Tenant-Id" = $tenantId
        }
    
    Write-Host "✓ Admin user created successfully!" -ForegroundColor Green
    Write-Host "  User ID: $($adminResponse.userId)" -ForegroundColor Gray
    Write-Host "  Email: $($adminResponse.email)" -ForegroundColor Gray
    Write-Host "  Roles: $($adminResponse.roles -join ', ')" -ForegroundColor Gray
    
    Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Yellow
    Write-Host "SUCCESS! Test tenant created" -ForegroundColor Green
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Yellow
    Write-Host "`nLogin credentials:" -ForegroundColor Cyan
    Write-Host "  Email:    admin@testcompany.com" -ForegroundColor White
    Write-Host "  Password: Admin@123456" -ForegroundColor White
    Write-Host "`nLogin at: http://localhost:3002/login" -ForegroundColor Cyan
    
} catch {
    Write-Host "✗ Failed to create admin user: $_" -ForegroundColor Red
    Write-Host "  Note: Tenant was created but admin user failed." -ForegroundColor Yellow
    Write-Host "  Tenant ID: $tenantId" -ForegroundColor Gray
    exit 1
}
