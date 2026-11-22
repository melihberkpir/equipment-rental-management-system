# Equipment Rental Management System

Spring Boot REST API for managing rental equipment, reservations, maintenance records, payments, and rental agreements.

## Requirements

- JDK 17
- Docker Desktop

Maven is not required because the project includes the Maven Wrapper.

## Run locally

Start PostgreSQL:

```bash
docker compose up -d --wait
```

Start the application:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw spring-boot:run
```

Flyway creates the schema and initial roles on startup.

API documentation is available at <http://localhost:8080/swagger-ui.html>.

## Configuration

The defaults work for local development. To override them, copy `.env.example` to `.env` for Docker Compose and export the same variables before starting the application.
