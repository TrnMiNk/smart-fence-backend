-- MANUAL MIGRATION ONLY. Do not execute automatically or against production
-- until the target schema has been checked against the preflight queries below.
-- This migration preserves all existing intrusion event rows. The new source
-- columns remain NULL for legacy rows; MySQL permits multiple NULL values in
-- the composite unique index.
--
-- Preflight:
--   SHOW CREATE TABLE intrusion_events;
-- Confirm event_id and occurred_at do not already exist and inspect any
-- existing custom indexes/constraints before applying the change.

ALTER TABLE intrusion_events
    ADD COLUMN event_id VARCHAR(36) NULL AFTER component_id,
    ADD COLUMN occurred_at DATETIME(3) NULL AFTER event_id,
    ADD UNIQUE KEY uq_intrusion_device_event_id (device_id, event_id);

-- Postflight:
--   SHOW CREATE TABLE intrusion_events;
--   SELECT COUNT(*) FROM intrusion_events WHERE event_id IS NULL;
--
