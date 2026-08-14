-- Эпик (FeatureEntity) was a bare grouping row (id/project_id/root_wishlist_id) with no content of its
-- own - JTBD/Kano/Cynefin all lived on the task/slice level instead. Operator correction (2026-07-21):
-- a wishlist should split into as many epics as the product actually needs (by narrative/theme), each
-- epic carries its OWN customer-facing JTBD/Kano/Cynefin, and its tasks carry a JTBD scoped to the epic
-- (not the customer) plus their own Cynefin. Six Sigma metric and TOC constraint ref live at BOTH levels
-- (operator decision) - epic gets an aggregate business metric, task keeps its own technical one.
ALTER TABLE features ADD COLUMN title VARCHAR(500);
ALTER TABLE features ADD COLUMN jtbd TEXT;
ALTER TABLE features ADD COLUMN kano_class VARCHAR(50);
ALTER TABLE features ADD COLUMN cynefin_domain VARCHAR(32);
ALTER TABLE features ADD COLUMN six_sigma_metric TEXT;
ALTER TABLE features ADD COLUMN toc_constraint_ref TEXT;
