# Usage Instructions

This file documents:
* instructions to start the application from fresh clone of the codebase,
* instructions to reach Swagger for manual testing.

## Prerequisites

* [Docker](https://docs.docker.com/get-docker/) with Docker Compose v2 (`docker compose`)
* git
* Java 17+ — only needed to build or run tests locally. Maven is not required; the
  repository ships with the Maven wrapper (`./mvnw`).

## Start the application from a fresh clone

1. Clone the repository and enter it:

   ```bash
   git clone <repository-url>
   cd time-deposit-take-home-kata
   ```

2. Create a `.env` file with database credentials. There are no defaults; Docker Compose
   refuses to start if any value is missing. `.env` is git-ignored — never commit it.

   ```bash
   cp .env.example .env
   ```

   Then edit `.env` and set every value, for example:

   ```dotenv
   POSTGRES_DB=timedeposit
   POSTGRES_USER=timedeposit
   POSTGRES_PASSWORD=<choose-a-password>
   ```

3. Build and start the application together with PostgreSQL:

   ```bash
   docker compose up --build
   ```

   On first start, Flyway creates the schema and loads seed data (six time deposits
   covering the `basic`, `student` and `premium` plans, plus a few withdrawals).
   The application is ready once the log shows `Started TimeDepositApplication`.

## Manual testing with Swagger UI

1. Open Swagger UI: <http://localhost:8080/swagger-ui/index.html>
2. Expand an endpoint, click **Try it out**, then **Execute**:
   * `GET /time-deposits` — returns all time deposits with their withdrawals, ordered by `id`
     ascending. Set the optional `afterId` parameter to fetch only deposits with a greater `id`.
   * `POST /time-deposits/calculate-interest` — runs the daily interest batch for the required
     `accounting_date` (e.g. `2026-03-09`); returns `204 No Content`. Monthly interest is applied
     to each deposit on its own monthly anniversary, after an initial no-accrual period of 30 days
     (basic, student) or 45 days (premium); then every deposit's `days` advances by one.
     With the seed data and `accounting_date=2026-03-09`, deposits 2, 3, 4 and 6 are due
     (deposit 4 is a student deposit older than a year, so its balance does not change).
     The first run must be for `2026-03-09`; see [Running interest calculation](#running-interest-calculation).

The OpenAPI contract that Swagger UI renders is available at
<http://localhost:8080/openapi.yaml>.

The same calls with `curl`:

```bash
curl --compressed http://localhost:8080/time-deposits
curl -X POST -i "http://localhost:8080/time-deposits/calculate-interest?accounting_date=2026-03-09"
```

## Running interest calculation

The accounting date is the date the batch runs for and may differ from the current calendar
date. Runs must be for consecutive accounting dates, one run per date:

* On a fresh database, the first run must be for `2026-03-09` (the database is initialized as if
  the run for `2026-03-08` had completed). Each next run must be for the following day.
* Repeating a completed date, skipping a date, or going back returns `409 Conflict`, and nothing is
  changed. The response body says which date is expected, e.g.

  ```json
  {"title":"Conflict","status":409,"detail":"Interest calculation for 2026-03-11 is not allowed: the latest run, for 2026-03-09, is completed. The next run must be for 2026-03-10."}
  ```

* If a run fails or is interrupted (e.g. an unknown plan type, or the application stopped), call the
  endpoint again with the **same** accounting date. It continues after the last committed batch, so
  every deposit is processed exactly once. Until then, other dates are rejected with `409`.
* If two runs overlap, only one makes progress; the other stops with `409`.

The state of the latest run can be inspected in the database:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT * FROM interest_calculation_process"'
```

JSON responses are gzip-compressed when the client sends `Accept-Encoding: gzip`
(`curl --compressed` does; Swagger UI and browsers do so automatically).

`GET /time-deposits` streams the response, so it works for any number of deposits. Results
are not guaranteed to be consistent while interest is being calculated concurrently.

Because results are ordered by `id`, an interrupted fetch can be resumed by passing the `id`
of the last time deposit received as `afterId` (exclusive):

```bash
curl --compressed "http://localhost:8080/time-deposits?afterId=3"   # returns deposits with id > 3
```

## Stop and reset

```bash
docker compose down      # stop containers, keep database data
docker compose down -v   # stop containers and delete database data (re-seeded on next start)
```

## Run the tests

```bash
./mvnw verify
```

Integration tests use Testcontainers, so Docker must be running. No `.env` is needed for
tests — Testcontainers provides the database connection.
