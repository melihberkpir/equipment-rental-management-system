# Equipment Rental Management System

[![CI](https://github.com/melihberkpir/equipment-rental-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/melihberkpir/equipment-rental-management-system/actions/workflows/ci.yml)

Spring Boot REST API for managing rental equipment, reservations, maintenance records, payments, and rental agreements.

## Features

- JWT authentication with `ADMIN` and `CUSTOMER` roles
- Equipment and category management with filtering, pagination, and sorting
- Availability checks based on equipment status, maintenance, and reservations
- Reservation lifecycle with server-side pricing and overlap prevention
- Payment records without an external payment provider
- Rental agreement PDF generation
- Swagger/OpenAPI documentation and PostgreSQL integration tests

## Technology

Java 17, Spring Boot 4, Spring Web MVC, Spring Data JPA/Hibernate, Spring Security, PostgreSQL 17, Flyway, Bean Validation, springdoc-openapi, OpenPDF, Testcontainers, and Maven.

## Documentation

- [API reference](docs/API.md)
- [Domain model, diagrams, and business rules](docs/DOMAIN.md)
- [Project specification](Equipment_Rental_Management_System_Specification.pdf)

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

GitHub Actions runs the build and tests for pushes to `main` and pull requests targeting `main`.

## Configuration

Both Docker Compose and the application read `.env`. Environment variables take precedence over it.
