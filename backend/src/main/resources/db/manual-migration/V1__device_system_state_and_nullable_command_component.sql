-- MANUAL MIGRATION ONLY. Do not execute automatically or against production
-- until the target schema has been checked against the preflight queries below.
-- Verified on MySQL 8.0.45 for smart_fence_iot.
--
-- Preflight:
--   SHOW CREATE TABLE devices;
--   SHOW CREATE TABLE device_commands;
--   SHOW CREATE TABLE device_components;
-- Confirm that device_commands.component_id is BIGINT NOT NULL, that
-- fk_command_component references (component_id, device_id) to
-- device_components(id, device_id) ON DELETE CASCADE, and that the supporting
-- index fk_command_component(component_id, device_id) exists.
-- Confirm that devices.system_state does not already exist.
-- Confirm that device_commands.ack_system_state does not already exist.
--
-- Existing devices receive UNKNOWN via the new column's default. Existing
-- command records retain their values; component_id, indexes, and FK semantics
-- are retained. ack_system_state records the systemState of the terminal ACK so
-- duplicate ACKs can be compared without trusting FAILED ACKs as device state.

ALTER TABLE devices
    ADD COLUMN system_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' AFTER status;

ALTER TABLE device_commands
    ADD COLUMN ack_system_state VARCHAR(20) NULL AFTER command_status,
    DROP FOREIGN KEY fk_command_component,
    MODIFY COLUMN component_id BIGINT NULL,
    ADD CONSTRAINT fk_command_component
        FOREIGN KEY (component_id, device_id)
        REFERENCES device_components (id, device_id)
        ON DELETE CASCADE;

-- Postflight:
--   SHOW CREATE TABLE devices;
--   SHOW CREATE TABLE device_commands;
--   SELECT system_state, COUNT(*) FROM devices GROUP BY system_state;
--   SELECT COUNT(*) FROM device_commands WHERE component_id IS NULL;
