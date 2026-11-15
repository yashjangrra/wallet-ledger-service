-- V2__allow_reversed_transfer_status.sql: Update status check constraint to permit REVERSED

ALTER TABLE transfers DROP CONSTRAINT IF EXISTS transfers_status_check;
ALTER TABLE transfers ADD CONSTRAINT transfers_status_check CHECK (status IN ('COMPLETED', 'REVERSED'));
