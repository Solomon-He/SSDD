CREATE TABLE model_versions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    arch_version VARCHAR(8) NOT NULL DEFAULT 'v2',
    num_classes TINYINT UNSIGNED NOT NULL DEFAULT 4,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    file_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    inference_config JSON NOT NULL,
    description VARCHAR(2000) NULL,
    uploaded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    miou DOUBLE NULL,
    precision_score DOUBLE NULL,
    recall_score DOUBLE NULL,
    dice DOUBLE NULL,
    fps DOUBLE NULL,
    metric_source VARCHAR(255) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_model_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_model_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT ck_model_v2 CHECK (arch_version = 'v2' AND num_classes = 4),
    CONSTRAINT ck_model_file_size CHECK (file_size_bytes > 0),
    CONSTRAINT ck_model_miou CHECK (miou IS NULL OR miou BETWEEN 0 AND 1),
    CONSTRAINT ck_model_precision CHECK (precision_score IS NULL OR precision_score BETWEEN 0 AND 1),
    CONSTRAINT ck_model_recall CHECK (recall_score IS NULL OR recall_score BETWEEN 0 AND 1),
    CONSTRAINT ck_model_dice CHECK (dice IS NULL OR dice BETWEEN 0 AND 1),
    CONSTRAINT ck_model_fps CHECK (fps IS NULL OR fps >= 0),
    INDEX idx_models_created (created_at, id),
    INDEX idx_models_sha256 (file_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One fixed row is the only source of truth for the active model.
CREATE TABLE model_runtime_state (
    id TINYINT UNSIGNED PRIMARY KEY,
    active_model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    state_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_runtime_singleton CHECK (id = 1),
    CONSTRAINT fk_runtime_model FOREIGN KEY (active_model_id) REFERENCES model_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO model_runtime_state (id, active_model_id, state_version) VALUES (1, NULL, 0);
