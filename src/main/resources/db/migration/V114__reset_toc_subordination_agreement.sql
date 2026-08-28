-- 2026-08-28: TocSubordinationLever wrote LEVER_OBSERVATIONS.AGREEMENT as TRUE when the policy and the
-- subordination rule merely COINCIDED, and FALSE when they differed. That is not the quantity
-- LeverPromotionService reads: there, TRUE means "the candidate was right and the incumbent was not",
-- established against real ground truth. Measured before this migration: 1408 TRUE and 1978 FALSE for
-- T1_TOC_SUBORDINATION, giving a rate of 0.416 against a 0.80 promotion threshold - a rate that measured
-- how often the rule was redundant, and that the lever could only raise by echoing the policy.
--
-- RECENCY_WINDOW is 14 days, so those rows would keep deciding the rate for two weeks after the code fix.
-- They are reset to NEITHER - the value LeverAgreement.compare returns when no ground truth is known -
-- which is exactly what they are: pairs recorded with no truth attached. The observations themselves are
-- kept; only a derived field computed from the wrong variable is cleared. Nothing about history is
-- rewritten, and the lever re-earns its rate from observations the product itself resolves.
UPDATE lever_observations
   SET agreement = 'NEITHER'
 WHERE lever_key = 'T1_TOC_SUBORDINATION'
   AND ground_truth_outcome IS NULL;

-- The cached counters on the state row are derived from the same mis-computed values.
UPDATE lever_promotion_state
   SET sample_count = 0, agreement_count = 0
 WHERE lever_key = 'T1_TOC_SUBORDINATION';
