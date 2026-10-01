-- Portable DDL: runs unchanged on H2 (MODE=PostgreSQL) and PostgreSQL. See docs/adr/0009.
-- Existing mappings take the default ACTIVE with a NULL deactivated_at; no row is rewritten or
-- removed, and click_event rows and their foreign key are untouched.

ALTER TABLE url_mapping ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE url_mapping ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;

-- Allowed states and the status/deactivated_at invariant. A future state (e.g. EXPIRED) must
-- replace this constraint in its own migration, which keeps every state change deliberate.
ALTER TABLE url_mapping ADD CONSTRAINT ck_url_mapping_lifecycle CHECK (
    (status = 'ACTIVE' AND deactivated_at IS NULL)
    OR (status = 'DEACTIVATED' AND deactivated_at IS NOT NULL)
);
