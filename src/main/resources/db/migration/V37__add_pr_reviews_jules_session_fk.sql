-- pr_reviews.jules_session_id had no FK constraint, so a manual jules_sessions cleanup could (and did)
-- leave orphaned pr_reviews rows silently invisible to any JOIN-based query. Add the missing FK with
-- ON DELETE CASCADE so a future reset can't repeat that mistake.
ALTER TABLE pr_reviews ADD CONSTRAINT fk_pr_reviews_jules_session
    FOREIGN KEY (jules_session_id) REFERENCES jules_sessions(id) ON DELETE CASCADE;
