-- V2: Adds the columns needed by the admin user-management UI to the users table.
--   * active     — soft enable/disable for login (UserDetails.isEnabled)
--   * created_at — audit timestamp (convention: every table has created_at)
--   * deleted_at — soft delete (medical/admin records are never hard-deleted)
-- Compatible with both PostgreSQL (prod) and H2 (dev).

ALTER TABLE users ADD COLUMN active     BOOLEAN   NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_users_deleted_at ON users (deleted_at);
