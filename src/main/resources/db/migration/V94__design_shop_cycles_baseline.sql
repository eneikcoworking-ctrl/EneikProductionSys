ALTER TABLE design_shop_cycles ADD COLUMN stitch_project_id VARCHAR(64);
ALTER TABLE design_shop_cycles ADD COLUMN stitch_screen_id VARCHAR(64);
ALTER TABLE design_shop_cycles ADD COLUMN declared_colors VARCHAR(1024);
ALTER TABLE design_shop_cycles ADD COLUMN declared_fonts VARCHAR(512);
ALTER TABLE design_shop_cycles ADD COLUMN edit_iteration_count INT NOT NULL DEFAULT 0;
