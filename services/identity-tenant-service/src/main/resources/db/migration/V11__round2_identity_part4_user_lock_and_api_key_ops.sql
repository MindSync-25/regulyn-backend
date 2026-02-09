-- V11__round2_identity_part4_user_lock_and_api_key_ops.sql
-- User lock fields + API key operation columns

ALTER TABLE identity.users
  ADD COLUMN IF NOT EXISTS locked_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS locked_reason text NULL,
  ADD COLUMN IF NOT EXISTS locked_by_user_id uuid NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_users_locked_by_user') THEN
    ALTER TABLE identity.users
      ADD CONSTRAINT fk_users_locked_by_user
      FOREIGN KEY (locked_by_user_id) REFERENCES identity.users(user_id);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_users_locked_at ON identity.users(locked_at);

ALTER TABLE identity.api_keys
  ADD COLUMN IF NOT EXISTS revoked_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS rotated_from_api_key_id uuid NULL,
  ADD COLUMN IF NOT EXISTS key_version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS prefix varchar(16) NULL,
  ADD COLUMN IF NOT EXISTS hash_alg varchar(50) NOT NULL DEFAULT 'SHA256';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_api_keys_rotated_from') THEN
    ALTER TABLE identity.api_keys
      ADD CONSTRAINT fk_api_keys_rotated_from
      FOREIGN KEY (rotated_from_api_key_id) REFERENCES identity.api_keys(api_key_id);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_api_keys_revoked_at ON identity.api_keys(revoked_at);
