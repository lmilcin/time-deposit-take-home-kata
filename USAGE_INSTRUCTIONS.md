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
   * `POST /time-deposits/calculate-interest` — applies monthly interest to the balances
     of all time deposits, then advances each deposit's `days` by one; returns `204 No Content`.

The OpenAPI contract that Swagger UI renders is available at
<http://localhost:8080/openapi.yaml>.

The same calls with `curl`:

```bash
curl --compressed http://localhost:8080/time-deposits
curl -X POST -i http://localhost:8080/time-deposits/calculate-interest
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
