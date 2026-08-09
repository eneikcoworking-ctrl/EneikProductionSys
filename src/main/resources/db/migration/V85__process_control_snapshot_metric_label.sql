-- Gricean quantity-optimal grounding follow-on (2026-08-07, Kaizen audit): sixSigmaMetric was computed
-- per-epic at compile time and shown in prompts/dashboards, but never attached to the real u-chart stream
-- it was meant to describe (confirmed live audit: presence-only gate, content never read semantically).
-- Purely descriptive - the epic owner's own operational definition of what a snapshot's numbers represent -
-- never used in any u-chart math (centerline/UCL/LCL/Western Electric signal detection are unaffected).
ALTER TABLE process_control_snapshots ADD COLUMN six_sigma_metric_label TEXT;
