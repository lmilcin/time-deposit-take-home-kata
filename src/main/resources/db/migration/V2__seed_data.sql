-- Sample data covering each plan type around its interest thresholds.
INSERT INTO time_deposits (id, plan_type, days, balance)
VALUES (1, 'basic', 15, 10000.00),    -- within first 30 days: no interest
       (2, 'basic', 60, 25000.00),    -- 1% yearly interest
       (3, 'student', 120, 5000.00),  -- 3% yearly interest
       (4, 'student', 400, 7500.00),  -- older than a year: no interest
       (5, 'premium', 40, 50000.00),  -- before day 45: no interest
       (6, 'premium', 90, 100000.00); -- 5% yearly interest

INSERT INTO withdrawals (id, time_deposit_id, amount, date)
VALUES (1, 2, 1000.00, '2026-09-15'),
       (2, 3, 250.00, '2026-10-01'),
       (3, 3, 100.00, '2026-10-20'),
       (4, 6, 5000.00, '2026-11-02');

-- Advance identity sequences past the explicitly inserted ids.
SELECT setval(pg_get_serial_sequence('time_deposits', 'id'), (SELECT MAX(id) FROM time_deposits));
SELECT setval(pg_get_serial_sequence('withdrawals', 'id'), (SELECT MAX(id) FROM withdrawals));
