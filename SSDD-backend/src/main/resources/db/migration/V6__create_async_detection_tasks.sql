CREATE TABLE detection_task_control (
    id TINYINT UNSIGNED PRIMARY KEY,
    CONSTRAINT ck_task_control_singleton CHECK (id=1)
) ENGINE=InnoDB;
INSERT INTO detection_task_control(id) VALUES(1);

CREATE TABLE detection_tasks (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_snapshot JSON NOT NULL,
    input_manifest JSON NOT NULL,
    image_count TINYINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'queued',
    attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts TINYINT UNSIGNED NOT NULL,
    attempt_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    attempt_outputs JSON NOT NULL,
    completed_images TINYINT UNSIGNED NOT NULL DEFAULT 0,
    result_batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_code VARCHAR(64) NULL,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    inputs_cleaned_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT uk_task_request UNIQUE(user_id,request_key),
    CONSTRAINT uk_task_result UNIQUE(result_batch_id),
    CONSTRAINT fk_task_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_task_model FOREIGN KEY(model_id) REFERENCES model_versions(id),
    CONSTRAINT fk_task_result FOREIGN KEY(result_batch_id) REFERENCES detection_batches(id),
    CONSTRAINT ck_task_status CHECK(status IN ('queued','running','retry_wait','succeeded','failed')),
    CONSTRAINT ck_task_count CHECK(image_count BETWEEN 1 AND 20 AND completed_images<=image_count),
    CONSTRAINT ck_task_attempts CHECK(max_attempts BETWEEN 1 AND 5 AND attempts<=max_attempts),
    CONSTRAINT ck_task_success CHECK((status='succeeded' AND result_batch_id IS NOT NULL AND completed_images=image_count) OR (status<>'succeeded' AND result_batch_id IS NULL)),
    INDEX idx_tasks_dispatch(status,next_attempt_at,created_at,id),
    INDEX idx_tasks_owner(user_id,created_at,id),
    INDEX idx_tasks_model(model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
