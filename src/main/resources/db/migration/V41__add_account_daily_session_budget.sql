-- Proactive local budget: previously the only defense against burning a whole day's Jules quota on
-- low-value self-generated work (design-review cosmetics, etc.) was reactive - Jules itself returning a
-- quota error after the fact (see AccountStatus.daily_limited). By then the day's capacity is already
-- gone, whatever it was spent on. Track dispatch count locally so dispatch selection can reserve it.
ALTER TABLE accounts ADD COLUMN sessions_dispatched_today INT DEFAULT 0 NOT NULL;
