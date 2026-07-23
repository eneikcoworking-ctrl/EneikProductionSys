ALTER TABLE accounts ADD COLUMN max_concurrent_sessions INT NULL;

UPDATE accounts SET max_concurrent_sessions = 15 WHERE name = 'eneikdru';
