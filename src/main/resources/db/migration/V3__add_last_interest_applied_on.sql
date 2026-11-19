-- Accounting date of the batch run that last calculated monthly interest for the deposit.
-- NULL means the monthly interest calculation has never run for the deposit.
ALTER TABLE time_deposits
    ADD COLUMN last_interest_applied_on DATE;
