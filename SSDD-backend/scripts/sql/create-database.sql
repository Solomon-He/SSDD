-- Run once using an account allowed to create the SSDD database.
-- Application startup manages tables through Flyway, never through schema.sql or auto-DDL.
CREATE DATABASE IF NOT EXISTS ssdd
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
