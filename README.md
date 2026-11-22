# Equipment Rental Management System

Spring Boot REST API for managing rental equipment, reservations, maintenance records, payments, and rental agreements.

## Requirements

- JDK 17
- Docker Desktop

Maven is not required because the project includes the Maven Wrapper.

## Run locally

Create the local configuration:

```bash
cp .env.example .env
```

Set `JWT_SECRET` in `.env` to the output of `openssl rand -hex 32`. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` to create the first administrator on startup.

Start PostgreSQL:

```bash
docker compose up -d --wait
```

Start the application:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw spring-boot:run
```

Flyway creates the schema and initial roles on startup.

API documentation is available at <http://localhost:8080/swagger-ui.html>. To call protected endpoints, sign in with `POST /api/auth/login` and paste the returned `accessToken` into **Authorize**.

## Tests

Tests start their own PostgreSQL container, so Docker must be running:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw test
```

## Configuration

Both Docker Compose and the application read `.env`. Environment variables take precedence over it.
