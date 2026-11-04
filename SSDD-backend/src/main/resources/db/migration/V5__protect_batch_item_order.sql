-- Batch idempotency is owned by detection_batches; its child keys are NULL.
-- Protect image order independently of the optional single-image request key.
ALTER TABLE detection_records
    ADD CONSTRAINT uk_record_batch_item UNIQUE (batch_id, batch_item_index);
