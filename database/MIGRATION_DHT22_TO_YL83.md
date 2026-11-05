# Manual migration: DHT22 to YL-83

This document describes the transition for an existing installation. It is documentation only and is not applied automatically. `database/schema.sql` is for a new installation; do not run it against an existing database.

## Existing DHT22 installations

- Keep the existing `sensor_readings` table and every row. Do not drop, truncate, recreate, or delete historical readings.
- Review and manually apply `backend/src/main/resources/db/manual-migration/V3__yl83_rain_sensor.sql` only when the preflight matches the original DHT22 layout and the YL-83 columns are absent. The script adds nullable rain columns and makes the old DHT columns nullable without rewriting their stored values.
- Keep legacy `temperature` and `humidity` columns in place. New YL-83 readings populate rain fields and leave the legacy columns NULL.
- Ensure the device has a `device_components` row with type `RAIN_SENSOR` for YL-83. Preserve unrelated LASER, BUZZER, CAMERA components and their IDs.
- Review current foreign keys and indexes before any additive change. Do not replace the database from the installer schema.

`database/schema.sql` describes a fresh YL-83 installation. It is not an upgrade script for an existing database.

## Current database status

The YL-83 migration has already been performed manually on the current development database. Do not run V3 there. This records the stated existing state only; no database operation is executed by this file or application startup.

For another existing installation, have its database owner review the current schema, take a backup, and apply only the appropriate manual migration once. These scripts are not automatically executed by the application.
