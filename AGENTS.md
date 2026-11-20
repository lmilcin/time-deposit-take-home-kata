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
* Hexagonal layout: `domain` and `application` (ports + `TimeDepositService`) are framework-free and wired in `config/ApplicationConfiguration`; adapters live in `adapter/in/web` and `adapter/out/persistence`. The shared `TimeDeposit` stays unchanged and `TimeDepositCalculator.updateBalance(List<TimeDeposit>)` keeps its signature (README), both in `org.ikigaidigital`; the calculator's implementation may change.
* Large result sets: the repository fetches lazily with keyset pagination (`id > :lastId ORDER BY id LIMIT :batchSize`), holding no connection between batches, so streams can be consumed while the HTTP response is written. Batch size is `time-deposits.batch-size`.
* `GET /time-deposits` results are ordered by `id` ascending, and this is part of the API contract: clients resume an interrupted fetch with `afterId` (exclusive). Any change to fetching (queries, batching, streaming) must preserve this ordering.
* Do not add `example` to optional parameters in `openapi.yaml`: Swagger UI pre-fills it in "Try it out", so requests silently send it (e.g. `afterId` skipping deposits).
* The GET response schema `TimeDepositList` is mapped via `schemaMappings` to `TimeDepositStream`, which serializes lazily. Keep the mapping when changing the contract.
* Response compression (`server.compression`) is done by the embedded server, so MockMvc cannot test it; use a `RANDOM_PORT` test (see `ResponseCompressionTest`). The streamed GET response has no Content-Length, so it is compressed regardless of `min-response-size`.
* Interest calculation persists each batch independently; recovery from an interrupted run is not handled yet. GET is not required to be consistent with a concurrent calculation.
* The app requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Locally they come from `.env` via Docker Compose; tests get them from Testcontainers (`@ServiceConnection`).
* `time_deposits.days` is the number of days the deposit has existed, counting the day it was made (starting at 1). This matches the calculator's thresholds (`> 30`, `> 45`, `< 366`) against the README rules.
* `POST /time-deposits/calculate-interest` is a daily batch run for a required `accounting_date`, which is the context of the whole run and may differ from the calendar date. Never use the system clock in interest logic.
* `TimeDepositCalculator.updateBalance` delegates both when monthly interest is due (`isDue`) and how much it is (`monthlyInterest`) to a strategy per plan type (`domain/interest/*PlanAccrualStrategy`, one class per plan even when logic is shared via `MonthlyAccrualSchedule`). Rounding (HALF_UP, 2 decimals) stays in the calculator. New plans need a new strategy bean in `ApplicationConfiguration`.
* The fixed `updateBalance` signature cannot carry the accounting date or `last_interest_applied_on`: the date is a `TimeDepositCalculator` constructor argument (one calculator per run, created in `TimeDepositService`), and `last_interest_applied_on` travels in `AccruingTimeDeposit extends TimeDeposit`, which the calculator updates when the calculation runs. A plain `TimeDeposit` is treated as never having had interest applied.
* Accrual rule: no accrual for the first 30 (basic, student) or 45 (premium) days; then interest is due every calendar month on the anniversaries of the accrual start (anchored, no month-end drift). Deposit date is derived as `accounting_date - (days - 1)`.
* `time_deposits.last_interest_applied_on` is the accounting date the monthly calculation last ran for the deposit, set even when the calculated interest is zero. Due = latest anniversary on or before the accounting date is after it (NULL = never ran, so existing deposits past an anniversary are due on their first run).
* Unknown plan types must fail the process (`UnknownPlanTypeException`) before their batch is calculated or persisted, deliberately deviating from the original calculator, which ignored them. `updateBalance` resolves all strategies before changing any deposit.
* `days` advances by one for every deposit on every run, after interest is calculated (interest uses the pre-increment `days`); this is done in `TimeDepositService`, never in the calculator.
* Tests that call `POST /time-deposits/calculate-interest` through MockMvc must be `@Transactional` so modified rows roll back and do not affect other tests sharing the container.
