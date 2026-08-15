-- Step 15 (closes F19): which markets a product actually serves, declared rather than inferred.
--
-- The regulatory floor renders every duty the corpus holds for DE and US. test-forty-sixth was a Russian
-- institution and was therefore shown duties that cannot apply to it - scope inflation dressed as
-- compliance, which is the thing the floor's own wording forbids.
--
-- NULL means undeclared, and undeclared deliberately keeps the previous behaviour of rendering both DE and
-- US. Showing a duty that does not apply costs wasted scope; omitting one that does costs a legal hole.
-- Those are not the same size of mistake, so the default fails towards the cheaper one.
ALTER TABLE projects ADD COLUMN target_markets VARCHAR(64);
