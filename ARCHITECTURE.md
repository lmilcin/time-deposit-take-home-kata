Spring Boot

The application is packaged as a Docker container and started with Docker Compose along with the database

Use contract-first approach with OpenAPI.
Openapi.yaml is served by the application as a static resource.
openapi-generator-maven-plugin generates sources from openapi.yaml (delegatePattern: "true") to ensure implementation always stays in sync with the contract.
Swagger UI is configured to use openapi.yaml and allow calling API endpoints.

PostgreSQL with Spring JDBC and Flyway to manage schema

# interest calculation process integrity

Only one interest calculation process can run at the same time.
Interest calculation can only be started in two situations:
1) When previous interest calculation has finished successfully and new interest calculation is marked with the next accounting date
2) When previous interest calculation has not finished yet and new interest calculation is marked with the same accounting date (resumption of possibly interrupted process)

If interest calculation is being resumed, two possible situations need to be resolved:
1) The previous process has already terminated. In this case the new process must start from where the previous process failed and ensure all time deposits are processed exactly once.
2) The previous process is still running. To ensure only one process can progress, each time deposit updates also needs to use optimistic lock on coordination record to guarantee only one update is committed.

Interest calculation coordination table interest_calculation_process
 - `accounting_date`: Date (required)
 - `last_processed_deposit_id`: Integer (required)
 - `last_status`: process_status (required)
 - `updated`: Timestamp with time zone (required)

process_status is an ENUM ('pending', 'completed')
`updated` contains current time as of time of each operation is only for debugging (to see how long ago the process issued the last update).
interest_calculation_process table contains a single record that is being updated with each batch of time deposits
interest_calculation_process record is updated in the same transaction as timeDeposit update. If the record cannot be updated properly, the entire transaction is rolled back and the process is failed.

interest_calculation_process record is effectively used for acquiring an optimistic lock on each batch of time deposit data

When starting interest calculation process, fetch the current contents of the record.
 * If endpoint accounting_date is next day from interest_calculation_process.accounting_date and last_status is completed, assume we are starting new batch
 * If endpoint accounting_date is the same as interest_calculation_process.accounting_date and process_status is pending, assume we are performing resumption
 * Fail process in any other case.

To start a new interest calculation process, perform:
```
UPDATE interest_calculation_process
SET
    accounting_date=:new_accounting_date,
    last_status='pending',
    last_processed_deposit_id=-1,
    updated=NOW()
WHERE
    accounting_date=:previous_accounting_date
```
This is considered successful if exactly one record was updated. If update fails or no records have been updated, this is considered a failure to acquire lock and the transaction needs to be failed.

Afterwards, for each batch of timeDeposit updates, perform:
```
UPDATE interest_calculation_process
SET
    last_processed_deposit_id=:id_of_last_time_deposit_in_current_batch,
    updated=NOW()
WHERE
    accounting_date=:current_accounting_date
    AND last_processed_deposit_id=:id_of_last_time_deposit_in_previous_batch
```
If processing first batch of timeDeposits, use last_processed_deposit_id received from the database. This also facilitates process resumption.
If this SQL updates anything else than a single record, it is considered a failure.

After last batch of time deposit data has been processed, perform:
```
UPDATE interest_calculation_process
SET
    last_status='completed',
    updated=NOW()
WHERE
    accounting_date=:current_accounting_date
    AND last_processed_deposit_id=:id_of_last_time_deposit_in_previous_batch
```
If this SQL updates anything else than a single record, it is considered a failure.


