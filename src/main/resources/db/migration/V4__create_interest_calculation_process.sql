-- Coordinates the daily interest calculation (see ARCHITECTURE.md, "interest calculation process integrity").
-- The single row acts as an optimistic lock: every step of a run updates it conditionally and fails unless
-- exactly one row was updated.

CREATE TYPE process_status AS ENUM ('pending', 'completed');

CREATE TABLE interest_calculation_process
(
    accounting_date           DATE           NOT NULL,
    last_processed_deposit_id INTEGER        NOT NULL,
    last_status               process_status NOT NULL,
    updated                   TIMESTAMPTZ    NOT NULL
);

-- At most one row: every row has the same index key.
CREATE UNIQUE INDEX interest_calculation_process_single_row ON interest_calculation_process ((true));

-- The run before the first one: the first allowed accounting date is 2026-03-09.
-- -1 is below every time deposit id, so a run starting from it processes all deposits.
INSERT INTO interest_calculation_process (accounting_date, last_processed_deposit_id, last_status, updated)
VALUES ('2026-03-08', -1, 'completed', NOW());
