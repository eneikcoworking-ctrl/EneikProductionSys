-- New 13th role: BARCAN-TAG-12 fixes the shared API contract (endpoints/DTOs) between backend
-- and frontend before they split into parallel implementation, so parallel work doesn't drift.
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-12', 'API Contract Designer', 'BARCAN-TAG-12_SOCIAL-CONTRACT.md');

UPDATE accounts
SET capabilities = 'BARCAN-TAG-00,BARCAN-TAG-01,BARCAN-TAG-02,BARCAN-TAG-03,BARCAN-TAG-04,BARCAN-TAG-05,BARCAN-TAG-06,BARCAN-TAG-07,BARCAN-TAG-08,BARCAN-TAG-09,BARCAN-TAG-10,BARCAN-TAG-11,BARCAN-TAG-12'
WHERE status <> 'decommissioned';
