* README.md and ARCHITECTURE.md contain additional context and design decisions for the project. Do not modify those.
* Never commit secrets. All secrets must be supplied through environmental variables with no defaults.
* Plan before performing any changes. Keep the plan short and get approval.
* Keep USAGE_INSTRUCTIONS.md up to date.
* When a change reveals non-obvious knowledge (gotchas, conventions, decisions), add it to the "Project notes" section below as part of the same change.

## Project notes

* API code is generated from `src/main/resources/static/openapi.yaml` with the delegate pattern. Implement `*ApiDelegate` beans; never write controllers by hand.
* Keep `<sourceFolder>.</sourceFolder>` in the openapi-generator config, otherwise IntelliJ registers the wrong generated-sources root.
* Do not set `springdoc.api-docs.enabled=false`; it also disables Swagger UI. Swagger UI is pointed at `/openapi.yaml`.
* Database tables and columns use snake_case (e.g. `time_deposits.plan_type`); schema changes go in new Flyway migrations under `src/main/resources/db/migration`.
* Name test classes `*Test` so surefire runs them (failsafe is not configured).
* The app requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Locally they come from `.env` via Docker Compose; tests get them from Testcontainers (`@ServiceConnection`).
