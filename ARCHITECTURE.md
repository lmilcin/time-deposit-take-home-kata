Spring Boot

The application is packaged as a Docker container and started with Docker Compose along with the database

Use contract-first approach with OpenAPI.
Openapi.yaml is served by the application as a static resource.
openapi-generator-maven-plugin generates sources from openapi.yaml (delegatePattern: "true") to ensure implementation always stays in sync with the contract.
Swagger UI is configured to use openapi.yaml and allow calling API endpoints.

PostgreSQL with Spring JDBC and Flyway to manage schema



