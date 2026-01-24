# Local Development Infrastructure

Docker Compose setup for local development environment.

## Services Included

- **Kafka**: Event streaming (with Zookeeper)
- **PostgreSQL**: Primary database
- **Redis**: Caching and sessions
- **OpenSearch**: Full-text search and analytics
- **Temporal**: Workflow orchestration

## Usage

### Start All Services
```bash
docker-compose up -d
```

### Start Specific Service
```bash
docker-compose up -d postgres
docker-compose up -d kafka
```

### Stop All Services
```bash
docker-compose down
```

### Stop and Remove Volumes
```bash
docker-compose down -v
```

## Service Endpoints

- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **Kafka**: localhost:9092
- **OpenSearch**: localhost:9200
- **Temporal UI**: localhost:8088

## Configuration Files

Each infrastructure component has its own folder with specific configurations:
- `kafka/` - Kafka broker and Zookeeper configs
- `postgres/` - Database initialization scripts
- `redis/` - Redis configuration
- `opensearch/` - OpenSearch settings
- `temporal/` - Temporal server config
