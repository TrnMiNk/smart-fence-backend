#!/usr/bin/env python3
"""Guarded, standalone component_type normalization runner for Smart Fence IoT.

Default mode is DRY-RUN. A write requires both --apply and the explicit
--confirm-device ESP32_SMART_FENCE_01 flags.

This runner deliberately requires a direct (not role-only) TRIGGER privilege
covering device_components so SHOW TRIGGERS can be trusted to reveal every
trigger attached to the table. It fails closed if that cannot be established.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import re
import sys
from typing import Any

try:
    import mysql.connector
    from mysql.connector import Error as MySQLError
except ImportError:
    print(
        "Missing dependency mysql-connector-python. Install it with: "
        "python -m pip install mysql-connector-python",
        file=sys.stderr,
    )
    raise SystemExit(2)


DATABASE = "smart_fence_iot"
DEVICE_ID = 2
DEVICE_CODE = "ESP32_SMART_FENCE_01"
TABLE = "device_components"

# id: (device_id, component_code, expected current type, target type)
COMPONENTS = {
    5: (2, "RAIN_SENSOR", "SENSOR", "RAIN_SENSOR"),
    6: (2, "LASER_SENSOR", "SENSOR", "LASER"),
    7: (2, "SIREN", "ACTUATOR", "BUZZER"),
    8: (2, "CAMERA", "CAMERA", "CAMERA"),
}

ROW_COLUMNS = (
    "id",
    "device_id",
    "component_code",
    "component_name",
    "component_type",
    "status",
    "created_at",
)


class GuardError(RuntimeError):
    """Raised when a precondition or postcondition is not satisfied."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Perform the update. Omit for the default read-only DRY-RUN.",
    )
    parser.add_argument(
        "--confirm-device",
        metavar="DEVICE_CODE",
        help="Required with --apply; must exactly match the target device code.",
    )
    return parser.parse_args()


def connection_options() -> dict[str, Any]:
    username = os.environ.get("SMART_FENCE_DB_USER") or input("MySQL username: ").strip()
    if not username:
        raise GuardError("A MySQL username is required.")

    password = os.environ.get("SMART_FENCE_DB_PASSWORD")
    if password is None:
        password = getpass.getpass("MySQL password: ")

    try:
        port = int(os.environ.get("SMART_FENCE_DB_PORT", "3306"))
    except ValueError as exc:
        raise GuardError("SMART_FENCE_DB_PORT must be an integer.") from exc

    return {
        "host": os.environ.get("SMART_FENCE_DB_HOST", "127.0.0.1"),
        "port": port,
        "database": os.environ.get("SMART_FENCE_DB_NAME", DATABASE),
        "user": username,
        "password": password,
        "autocommit": False,
        "connection_timeout": 10,
    }


def direct_trigger_grant_covers_table(grants: list[str]) -> bool:
    """Accept explicit direct grants at global, schema, or table scope only.

    Role-only grants intentionally fail closed: resolving active and nested
    role grants portably is outside this runner's safety boundary.
    """
    allowed_scopes = {
        "*.*",
        f"{DATABASE}.*",
        f"{DATABASE}.{TABLE}",
    }

    for grant in grants:
        match = re.match(
            r"^\s*GRANT\s+(.+?)\s+ON\s+(.+?)\s+TO\s+",
            grant,
            flags=re.IGNORECASE,
        )
        if not match:
            continue

        privilege_text = match.group(1).strip().upper()
        scope = match.group(2).replace("`", "").replace(" ", "").lower()
        privileges = {part.strip() for part in privilege_text.split(",")}

        if scope in allowed_scopes and (
            "TRIGGER" in privileges or "ALL PRIVILEGES" in privileges
        ):
            return True

    return False


def fetch_rows(cursor: Any, *, for_update: bool) -> dict[int, dict[str, Any]]:
    query = f"""
        SELECT {", ".join(ROW_COLUMNS)}
        FROM {TABLE}
        WHERE id IN (5, 6, 7, 8)
        ORDER BY id
        {"FOR UPDATE" if for_update else ""}
    """
    cursor.execute(query)
    rows = cursor.fetchall()
    return {int(row["id"]): row for row in rows}


def verify_device_and_rows(
    locked_device: dict[str, Any],
    rows: dict[int, dict[str, Any]],
    *,
    expected_types: dict[int, str],
) -> None:
    if (
        int(locked_device["id"]) != DEVICE_ID
        or locked_device["device_code"] != DEVICE_CODE
    ):
        raise GuardError(
            f"Device guard failed: expected id={DEVICE_ID}, "
            f"device_code={DEVICE_CODE!r} on the locked devices row."
        )

    if set(rows) != set(COMPONENTS):
        raise GuardError(
            "Component guard failed: expected exactly IDs 5, 6, 7, and 8; "
            f"found {sorted(rows)}."
        )

    for component_id, (device_id, code, _old_type, _new_type) in COMPONENTS.items():
        row = rows[component_id]
        if (
            int(row["device_id"]) != device_id
            or row["component_code"] != code
            or row["component_type"] != expected_types[component_id]
        ):
            raise GuardError(
                f"Component guard failed for id={component_id}: "
                "device_id, component_code, or component_type did not match."
            )


def print_rows(label: str, rows: dict[int, dict[str, Any]]) -> None:
    printable = [rows[key] for key in sorted(rows)]
    print(f"{label}: {json.dumps(printable, ensure_ascii=False, default=str, indent=2)}")


def run() -> int:
    args = parse_args()
    write_mode = bool(args.apply)
    if write_mode and args.confirm_device != DEVICE_CODE:
        raise GuardError(
            "Write mode requires both --apply and "
            f"--confirm-device {DEVICE_CODE}."
        )
    if not write_mode and args.confirm_device:
        raise GuardError("--confirm-device is only valid together with --apply.")

    options = connection_options()
    connection = None
    cursor = None
    transaction_started = False
    commit_started = False
    before: dict[int, dict[str, Any]] = {}
    after: dict[int, dict[str, Any]] = {}
    changed_rows: int | None = None
    result_status = "ROLLBACK (no commit)"

    print(f"Mode: {'WRITE' if write_mode else 'DRY-RUN'}")
    print(f"Target database/device: {DATABASE} / {DEVICE_CODE} (id={DEVICE_ID})")

    try:
        connection = mysql.connector.connect(**options)
        cursor = connection.cursor(dictionary=True)

        # The connector creates a fresh, dedicated connection. No application
        # pool or existing transaction is reused by this runner.
        connection.start_transaction(isolation_level="REPEATABLE READ")
        transaction_started = True

        cursor.execute("SELECT DATABASE() AS current_database")
        current_database = cursor.fetchone()["current_database"]
        if current_database != DATABASE:
            raise GuardError(
                f"Wrong database selected: expected {DATABASE!r}, "
                f"found {current_database!r}."
            )

        cursor.execute(
            """
            SELECT ENGINE
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
            """,
            (TABLE,),
        )
        engine_rows = cursor.fetchall()
        if len(engine_rows) != 1 or engine_rows[0]["ENGINE"] != "InnoDB":
            raise GuardError(
                "device_components engine is not verifiably InnoDB; refusing to run."
            )

        # Also require the devices row to be transactional so its FOR UPDATE
        # lock protects the device_code check throughout this transaction.
        cursor.execute(
            """
            SELECT ENGINE
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'devices'
            """
        )
        device_engine_rows = cursor.fetchall()
        if len(device_engine_rows) != 1 or device_engine_rows[0]["ENGINE"] != "InnoDB":
            raise GuardError("devices engine is not verifiably InnoDB; refusing to run.")

        cursor.execute(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = %s
              AND COLUMN_NAME IN ('status', 'component_status')
            """,
            (TABLE,),
        )
        status_columns = {row["COLUMN_NAME"] for row in cursor.fetchall()}
        if "status" not in status_columns:
            raise GuardError(
                "Expected device_components.status column was not found; refusing to run."
            )

        cursor.execute(
            "SELECT id, device_code FROM devices WHERE id = %s FOR UPDATE",
            (DEVICE_ID,),
        )
        locked_devices = cursor.fetchall()
        if len(locked_devices) != 1:
            raise GuardError(f"Could not lock exactly device id={DEVICE_ID}.")
        locked_device = locked_devices[0]

        # The repository schema includes created_at, but verify every selected
        # column against the connected schema before relying on that definition.
        cursor.execute(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = %s
            """,
            (TABLE,),
        )
        actual_columns = {row["COLUMN_NAME"] for row in cursor.fetchall()}
        missing_columns = set(ROW_COLUMNS) - actual_columns
        if missing_columns:
            raise GuardError(
                "device_components is missing selected columns: "
                + ", ".join(sorted(missing_columns))
            )

        # This locking read acquires a metadata lock on device_components.
        # The transaction retains it through the subsequent SHOW TRIGGERS and
        # until COMMIT/ROLLBACK, blocking concurrent trigger DDL in between.
        before = fetch_rows(cursor, for_update=True)

        expected_before = {
            component_id: details[2]
            for component_id, details in COMPONENTS.items()
        }
        verify_device_and_rows(
            locked_device,
            before,
            expected_types=expected_before,
        )

        # INFORMATION_SCHEMA.TRIGGERS and SHOW TRIGGERS are privilege-filtered.
        # Require an explicit direct grant covering this table before trusting
        # the trigger listing. Role-only grants are rejected. This check and
        # SHOW TRIGGERS now run while the component table's metadata lock is held.
        cursor.execute("SHOW GRANTS FOR CURRENT_USER")
        grants = [next(iter(row.values())) for row in cursor.fetchall()]
        if not direct_trigger_grant_covers_table(grants):
            raise GuardError(
                "Cannot prove this account has a direct TRIGGER privilege covering "
                "smart_fence_iot.device_components. Grant TRIGGER directly at global, "
                "schema, or table scope, then rerun; role-only grants are not accepted."
            )

        cursor.execute(
            "SHOW TRIGGERS FROM `smart_fence_iot` LIKE 'device_components'"
        )
        triggers = cursor.fetchall()
        if triggers:
            names = [str(row.get("Trigger", "<unnamed>")) for row in triggers]
            raise GuardError(
                "device_components has UPDATE/other triggers; refusing to risk side effects: "
                + ", ".join(names)
            )

        print_rows("Before", before)

        planned = {
            component_id: {**row, "component_type": COMPONENTS[component_id][3]}
            for component_id, row in before.items()
        }

        if not write_mode:
            connection.rollback()
            transaction_started = False
            result_status = "ROLLBACK (DRY-RUN; no UPDATE executed)"
            print_rows("Planned after (not written)", planned)
            print("Rows changed: 0")
            print(f"Transaction: {result_status}")
            return 0

        cursor.execute(
            """
            UPDATE device_components
            SET component_type = CASE id
                WHEN 5 THEN 'RAIN_SENSOR'
                WHEN 6 THEN 'LASER'
                WHEN 7 THEN 'BUZZER'
            END
            WHERE id IN (5, 6, 7)
              AND device_id = 2
              AND (
                    (id = 5 AND component_code = 'RAIN_SENSOR' AND component_type = 'SENSOR')
                 OR (id = 6 AND component_code = 'LASER_SENSOR' AND component_type = 'SENSOR')
                 OR (id = 7 AND component_code = 'SIREN' AND component_type = 'ACTUATOR')
              )
            """
        )
        changed_rows = cursor.rowcount
        if changed_rows != 3:
            raise GuardError(
                f"Expected exactly 3 changed rows; MySQL reported {changed_rows}."
            )

        after = fetch_rows(cursor, for_update=True)
        expected_after = {component_id: details[3] for component_id, details in COMPONENTS.items()}
        verify_device_and_rows(
            locked_device,
            after,
            expected_types=expected_after,
        )

        # Ensure every captured field other than component_type stayed identical,
        # including CAMERA ID 8 and status/code/name/device_id/created_at.
        for component_id in COMPONENTS:
            expected_row = dict(before[component_id])
            expected_row["component_type"] = COMPONENTS[component_id][3]
            if after[component_id] != expected_row:
                raise GuardError(
                    f"Post-update row {component_id} differs outside the expected type mapping."
                )

        print_rows("After (verified, pending commit)", after)
        print(f"Rows changed: {changed_rows}")

        commit_started = True
        connection.commit()
        transaction_started = False
        result_status = "COMMIT"
        print(f"Transaction: {result_status}")
        return 0

    except BaseException as exc:
        if transaction_started and connection is not None:
            try:
                connection.rollback()
                transaction_started = False
                result_status = (
                    "ROLLBACK attempted (COMMIT outcome may be unknown)"
                    if commit_started
                    else "ROLLBACK"
                )
            except BaseException:
                result_status = "ROLLBACK FAILED; transaction state requires DBA verification"
        message = str(exc)
        secret = options.get("password")
        if secret:
            message = message.replace(secret, "[REDACTED]")
        print(f"Error: {type(exc).__name__}: {message}", file=sys.stderr)
        if before:
            print_rows("Before", before)
        if after:
            print_rows("After (observed)", after)
        print(f"Rows changed: {changed_rows if changed_rows is not None else 0}")
        print(f"Transaction: {result_status}")
        return 1

    finally:
        if cursor is not None:
            cursor.close()
        if connection is not None:
            connection.close()


if __name__ == "__main__":
    try:
        raise SystemExit(run())
    except GuardError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        print("Rows changed: 0")
        print("Transaction: ROLLBACK (no transaction or UPDATE was started)")
        raise SystemExit(1)
