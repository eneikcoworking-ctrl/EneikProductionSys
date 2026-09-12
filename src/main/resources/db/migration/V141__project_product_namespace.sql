-- V141: Product namespace attribute on projects (INDEXICAL_CONTEXT_LOCK / D006 / Law 26).
-- Binds file scope predictions and collision guards to the product's actual package and stack,
-- rather than carrying the factory's own package or static Next.js/Prisma assumptions into client repos.

ALTER TABLE projects ADD COLUMN product_namespace VARCHAR(256);

UPDATE projects
SET product_namespace = 'com.eneik.epidemiology'
WHERE slug = 'test-fiftieth'
   OR repository_name LIKE '%fiftieth%'
   OR repository_name LIKE '%epidemiology%';
