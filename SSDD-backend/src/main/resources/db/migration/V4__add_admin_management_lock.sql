-- Serializes administrator bootstrap and role changes across application instances.
CREATE TABLE system_locks (
    lock_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO system_locks(lock_name) VALUES ('admin-management');
