# Support Mode API Testing Script
# Make sure identity-tenant-service is running on port 8081

$baseUrl = "http://localhost:8081"

Write-Host "=== Testing Support Mode Endpoints ===" -ForegroundColor Cyan

# Note: These endpoints require REGULYN_SUPER_ADMIN role
# You'll need to add Authorization header with a valid JWT token

# Example tenant ID (replace with actual tenant ID from your database)
$tenantId = "550e8400-e29b-41d4-a716-446655440000"

Write-Host "`n1. Testing Support Audit Recording (POST /admin/platform/support/audit)" -ForegroundColor Yellow

$auditPayload = @{
    tenantId = $tenantId
    action = "VIEW_TENANT_CONFIG"
    description = "Viewed tenant configuration during troubleshooting session"
    metadata = @{
        ipAddress = "192.168.1.100"
        userAgent = "Mozilla/5.0"
        affectedResources = @("tenant-config", "user-settings")
    }
} | ConvertTo-Json -Depth 10

Write-Host "Payload:" -ForegroundColor Gray
Write-Host $auditPayload -ForegroundColor Gray

# Uncomment when you have auth token:
# $headers = @{
#     "Content-Type" = "application/json"
#     "Authorization" = "Bearer YOUR_JWT_TOKEN_HERE"
# }
# $response = Invoke-RestMethod -Uri "$baseUrl/admin/platform/support/audit" -Method Post -Headers $headers -Body $auditPayload
# Write-Host "Response: $($response | ConvertTo-Json)" -ForegroundColor Green

Write-Host "`nCommand to run (with auth):"
Write-Host "curl -X POST $baseUrl/admin/platform/support/audit \`" -ForegroundColor Magenta
Write-Host "  -H 'Content-Type: application/json' \`" -ForegroundColor Magenta
Write-Host "  -H 'Authorization: Bearer YOUR_TOKEN' \`" -ForegroundColor Magenta
Write-Host "  -d '$($auditPayload -replace '"', '\"')'" -ForegroundColor Magenta

Write-Host "`n2. Testing Tenant Audit Timeline (GET /admin/platform/tenants/{tenantId}/audit/events)" -ForegroundColor Yellow
Write-Host "Command to run:"
Write-Host "curl '$baseUrl/admin/platform/tenants/$tenantId/audit/events?page=0&size=20' \`" -ForegroundColor Magenta
Write-Host "  -H 'Authorization: Bearer YOUR_TOKEN'" -ForegroundColor Magenta

Write-Host "`n3. Testing Evidence Bundles List (GET /admin/platform/tenants/{tenantId}/evidence/bundles)" -ForegroundColor Yellow
Write-Host "Command to run:"
Write-Host "curl '$baseUrl/admin/platform/tenants/$tenantId/evidence/bundles?page=0&size=10' \`" -ForegroundColor Magenta
Write-Host "  -H 'Authorization: Bearer YOUR_TOKEN'" -ForegroundColor Magenta

Write-Host "`n=== Quick Test (Without Auth - will get 401/403) ===" -ForegroundColor Cyan

Write-Host "`nTesting endpoint availability..."
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -Method Get -ErrorAction Stop
    Write-Host "✓ Service is UP and running!" -ForegroundColor Green
} catch {
    Write-Host "✗ Service is not responding. Make sure it's running on port 8081" -ForegroundColor Red
}

Write-Host "`n=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Get a valid tenant from the database: SELECT tenant_id FROM identity.tenants LIMIT 1"
Write-Host "2. Get admin JWT token by logging in as a REGULYN_SUPER_ADMIN user"
Write-Host "3. Run the curl commands above with actual token and tenant ID"
Write-Host "4. Or start the frontend to test via the Support Mode UI"
