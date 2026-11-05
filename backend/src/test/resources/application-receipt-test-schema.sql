CREATE TABLE devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_code VARCHAR(50) NOT NULL UNIQUE,
    device_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    system_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_seen DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE device_components (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    component_code VARCHAR(50) NOT NULL,
    component_name VARCHAR(100) NOT NULL,
    component_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_component_device_code UNIQUE (device_id, component_code),
    CONSTRAINT fk_receipt_component_device FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE TABLE intrusion_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    component_id BIGINT NULL,
    event_id VARCHAR(36) NULL,
    occurred_at DATETIME(3) NULL,
    event_type VARCHAR(30) NOT NULL,
    event_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    description TEXT NULL,
    detected_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    closed_at DATETIME NULL,
    CONSTRAINT uq_intrusion_device_event_id UNIQUE (device_id, event_id),
    INDEX idx_intrusion_detected_at (detected_at),
    INDEX idx_intrusion_status (event_status),
    CONSTRAINT fk_receipt_event_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_receipt_event_component FOREIGN KEY (component_id) REFERENCES device_components(id)
);

CREATE TABLE sensor_readings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    component_id BIGINT NOT NULL,
    rain_detected TINYINT(1) NULL,
    rain_level INT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensor_recorded_at (recorded_at),
    INDEX idx_sensor_component_time (component_id, recorded_at),
    CONSTRAINT fk_receipt_reading_component FOREIGN KEY (component_id) REFERENCES device_components(id)
);
