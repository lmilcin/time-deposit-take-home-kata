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
* Hexagonal layout: `domain` and `application` (ports + `TimeDepositService`) are framework-free and wired in `config/ApplicationConfiguration`; adapters live in `adapter/in/web` and `adapter/out/persistence`. The shared `TimeDeposit` and `TimeDepositCalculator` stay unchanged in `org.ikigaidigital`.
* Large result sets: the repository fetches lazily with keyset pagination (`id > :lastId ORDER BY id LIMIT :batchSize`), holding no connection between batches, so streams can be consumed while the HTTP response is written. Batch size is `time-deposits.batch-size`.
* `GET /time-deposits` results are ordered by `id` ascending, and this is part of the API contract: clients resume an interrupted fetch with `afterId` (exclusive). Any change to fetching (queries, batching, streaming) must preserve this ordering.
* The GET response schema `TimeDepositList` is mapped via `schemaMappings` to `TimeDepositStream`, which serializes lazily. Keep the mapping when changing the contract.
* Response compression (`server.compression`) is done by the embedded server, so MockMvc cannot test it; use a `RANDOM_PORT` test (see `ResponseCompressionTest`). The streamed GET response has no Content-Length, so it is compressed regardless of `min-response-size`.
* Interest calculation persists each batch independently; recovery from an interrupted run is not handled yet. GET is not required to be consistent with a concurrent calculation.
* The app requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Locally they come from `.env` via Docker Compose; tests get them from Testcontainers (`@ServiceConnection`).
* `time_deposits.days` is the number of days the deposit has existed, counting the day it was made (starting at 1). This matches the calculator's thresholds (`> 30`, `> 45`, `< 366`) against the README rules.
