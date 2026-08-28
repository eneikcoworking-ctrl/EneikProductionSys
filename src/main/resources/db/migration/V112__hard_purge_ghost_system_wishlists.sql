-- Flyway migration V112: Permanently hard-purge ghost wishlists, false recovery items, and misrouted factory metrics
-- Demarcation: Pure domain wishlists only

DELETE FROM wishlist
WHERE source IN ('delivery_never_reached_main', 'design_review_concern_pattern', 'gemini_observer')
   OR content LIKE '%Work that was reported as delivered never reached the main branch%'
   OR content LIKE '%Six Sigma u-chart out of control%'
   OR content LIKE '%orchestrator_tasks%';
