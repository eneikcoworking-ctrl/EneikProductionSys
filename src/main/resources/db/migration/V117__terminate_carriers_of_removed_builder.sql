-- 2026-08-29, §11 replaced by its own conclusion.
--
-- §10 removed the prompt builder that pasted a whole PR diff into a request. §11 then tried to keep the
-- tasks it had already written out of dispatch by filtering them where tasks are selected - and that was a
-- patch, proven so within the hour: the filter went on the queued list, review tasks are selected by a
-- second list, and the same prompt reappeared on account after account through the door nobody had shut.
-- Every reader would have had to learn the same exclusion, forever, and one missed reader brings it back.
--
-- Invariant 8 does not say "exclude at each decision". It says an element structurally unable to reach done
-- must LEAVE the set. These carriers can never be sent: the recipient refuses the text and no work of ours
-- changes that. So they leave, once, here - and every filter written for them is deleted with this change.
--
-- Identified by the trace of the builder that wrote them, absent from the current sources by construction
-- (measured 2026-08-29: zero occurrences in src/main/java, 31 tasks carrying it, 30 already terminal).
-- Terminal statuses are not touched, so this cannot resurrect or overwrite a decided task.
UPDATE tasks
   SET status = 'failed'
 WHERE description LIKE '%Diff to review:%'
   AND status NOT IN ('done', 'failed', 'spike_completed');
