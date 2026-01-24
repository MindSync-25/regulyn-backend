# Development Tools & Scripts

Automation scripts for build, deployment, and development workflows.

## Scripts

### Build Scripts
- `build-all.sh` - Build all services
- `build-service.sh <service-name>` - Build specific service
- `test-all.sh` - Run all tests

### Docker Scripts
- `docker-build-all.sh` - Build all Docker images
- `docker-push-all.sh` - Push all images to registry
- `docker-clean.sh` - Clean up unused images

### Database Scripts
- `db-migrate.sh` - Run database migrations
- `db-seed.sh` - Seed test data
- `db-reset.sh` - Reset local database

### Development Scripts
- `dev-setup.sh` - Initial development environment setup
- `generate-openapi.sh` - Generate OpenAPI specs from code
- `format-code.sh` - Format all Java code

### Deployment Scripts
- `deploy-local.sh` - Deploy to local Docker Compose
- `deploy-dev.sh` - Deploy to dev environment
- `deploy-staging.sh` - Deploy to staging

## Usage

Make scripts executable:
```bash
chmod +x tools/scripts/*.sh
```

Run a script:
```bash
./tools/scripts/build-all.sh
```

## Configuration

Scripts read configuration from:
- Environment variables
- `.env` files
- `config/` directory
