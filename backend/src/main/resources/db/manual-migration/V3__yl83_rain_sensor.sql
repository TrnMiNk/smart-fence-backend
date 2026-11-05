-- MANUAL MIGRATION ONLY. Never run automatically or against an unverified schema.
--
-- For installations still using the original DHT22 sensor_readings layout:
--   temperature DECIMAL(5,2) NULL
--   humidity    DECIMAL(5,2) NOT NULL
--
-- Preflight:
--   SHOW CREATE TABLE sensor_readings;
--   SELECT COUNT(*) AS historical_rows FROM sensor_readings;
-- Confirm rain_detected and rain_level do not already exist, and confirm the
-- legacy temperature/humidity definitions match the MODIFY clauses below.
-- Back up the database before applying this one-time migration.
--
-- Existing reading rows and their temperature/humidity values are retained.
-- New YL-83 rows leave those legacy columns NULL and write rain_detected/rain_level.

ALTER TABLE sensor_readings
    ADD COLUMN rain_detected TINYINT(1) NULL AFTER component_id,
    ADD COLUMN rain_level INT NULL AFTER rain_detected,
    MODIFY COLUMN temperature DECIMAL(5,2) NULL,
    MODIFY COLUMN humidity DECIMAL(5,2) NULL;

-- Postflight:
--   SHOW CREATE TABLE sensor_readings;
--   SELECT COUNT(*) AS historical_rows FROM sensor_readings;
--   SELECT COUNT(*) AS rows_without_rain_values
--     FROM sensor_readings WHERE rain_detected IS NULL AND rain_level IS NULL;
-- Do not apply this migration to fresh-install schema.sql or to an installation
-- where the YL-83 columns were already added; the repository records that the
-- current development database has already been migrated manually.
