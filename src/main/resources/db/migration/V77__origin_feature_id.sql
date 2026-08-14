ALTER TABLE features ADD COLUMN origin_feature_id UUID;
ALTER TABLE tasks ADD COLUMN origin_feature_id UUID;
ALTER TABLE wishlist ADD COLUMN origin_feature_id UUID;

ALTER TABLE features ADD COLUMN dismissed_at TIMESTAMP;
