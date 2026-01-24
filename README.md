# DPDP Compliance Platform - Backend Services

Production-ready mono-repo for DPDP (Digital Personal Data Protection Act, India) compliance platform backend services.

## Architecture

Multi-service, event-driven microservices architecture built for compliance and scalability.

### Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3
- **Event Streaming**: Apache Kafka
- **Workflow Orchestration**: Temporal
- **Databases**: PostgreSQL, Redis
- **Search**: OpenSearch
- **Observability**: OpenTelemetry, Prometheus

## Repository Structure

```
regulyn-backend/
├── libs/                          # Shared libraries
│   ├── lib-common/                # Common utilities
│   ├── lib-auth/                  # Authentication & authorization
│   ├── lib-events/                # Event-driven utilities
│   ├── lib-evidence/              # Evidence management
│   ├── lib-observability/         # Logging, tracing, metrics
│   └── lib-temporal/              # Temporal workflow support
│
├── services/                      # Microservices
│   ├── identity-tenant-service/   # Identity & tenant management
│   ├── consent-service/           # Consent management
│   ├── ropa-inventory-service/    # ROPA & data inventory
│   ├── dsar-grievance-service/    # DSAR & grievance handling
│   ├── retention-deletion-service/ # Retention & deletion
│   ├── incident-breach-service/   # Incident & breach management
│   ├── nominee-service/           # Nominee management
│   ├── children-guardian-service/ # Children & guardian consent
│   ├── vendor-sharing-service/    # Vendor & data sharing
│   ├── employee-data-service/     # Employee data management
│   ├── evidence-reporting-service/ # Evidence & reporting
│   ├── notification-service/      # Multi-channel notifications
│   ├── connector-service/         # External integrations
│   └── scanner-service/           # Data discovery & scanning
│
├── infra/                         # Infrastructure
│   └── local/                     # Local development setup
│       ├── kafka/
│       ├── postgres/
│       ├── redis/
│       ├── opensearch/
│       └── temporal/
│
├── docs/                          # Documentation
│   ├── diagrams/                  # Architecture diagrams
│   ├── api/                       # API documentation
│   ├── workflows/                 # Workflow documentation
│   └── event-schemas/             # Event schema definitions
│
└── tools/                         # Development tools
    └── scripts/                   # Build & deployment scripts
```

## Services Overview

### Core Services
- **identity-tenant-service**: Multi-tenant identity and access management
- **consent-service**: Consent lifecycle management
- **ropa-inventory-service**: Record of Processing Activities
- **dsar-grievance-service**: Data Subject Access Requests

### Compliance Services
- **retention-deletion-service**: Automated retention and deletion
- **incident-breach-service**: Security incident management
- **evidence-reporting-service**: Compliance evidence and reporting

### Supporting Services
- **notification-service**: Email, SMS, in-app notifications
- **connector-service**: Third-party integrations
- **scanner-service**: Data discovery and classification

### Specialized Services
- **nominee-service**: Deceased user nominee management
- **children-guardian-service**: Parental consent for minors
- **vendor-sharing-service**: Third-party data sharing
- **employee-data-service**: Employee data compliance

## Development

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.9+

### Local Setup

1. Start local infrastructure:
```bash
cd infra/local
docker-compose up -d
```

2. Build all services:
```bash
mvn clean install
```

3. Run a service:
```bash
cd services/<service-name>
mvn spring-boot:run
```

## Event-Driven Architecture

Services communicate via Kafka events. See [docs/event-schemas](docs/event-schemas/) for event definitions.

## Workflow Orchestration

Long-running processes use Temporal workflows. See [docs/workflows](docs/workflows/) for workflow definitions.

## Contributing

See individual service README files for service-specific development guidelines.

## License

Proprietary - All rights reserved.