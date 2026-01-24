# API Documentation

REST API specifications for all services.

## Format
OpenAPI 3.0 (Swagger) specifications

## Structure

Each service has its own OpenAPI spec file:
- `identity-tenant-api.yaml`
- `consent-api.yaml`
- `ropa-inventory-api.yaml`
- `dsar-grievance-api.yaml`
- etc.

## Common Patterns

### Authentication
All APIs use JWT Bearer tokens:
```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### Error Responses
Standard error response format:
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "timestamp": "2026-01-24T10:00:00Z",
    "path": "/api/endpoint"
  }
}
```

### Pagination
Standard pagination parameters:
- `page` (default: 0)
- `size` (default: 20)
- `sort` (e.g., "createdAt,desc")

## Tools
- Swagger UI
- Swagger Editor
- Postman collections
