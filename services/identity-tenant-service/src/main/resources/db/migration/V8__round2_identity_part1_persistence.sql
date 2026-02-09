-- V8__round2_identity_part1_persistence.sql
-- Round 2 Part 1 persistence additions only

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE n.nspname = 'identity'
      AND t.relname = 'tenants'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE identity.tenants DROP CONSTRAINT %I', r.conname);
  END LOOP;
END$$;

ALTER TABLE identity.tenants
  ADD CONSTRAINT chk_tenants_status_round2
  CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','DELETED'));

ALTER TABLE identity.tenants
  ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE identity.tenants
  ADD COLUMN IF NOT EXISTS activated_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS suspended_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS admin_bootstrapped_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS admin_bootstrap_user_id uuid NULL,
  ADD COLUMN IF NOT EXISTS compliance_hold boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS compliance_hold_reason text NULL,
  ADD COLUMN IF NOT EXISTS delete_requested_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS read_only boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS read_only_reason text NULL,
  ADD COLUMN IF NOT EXISTS read_only_since timestamptz NULL,
  ADD COLUMN IF NOT EXISTS plan_code text NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_tenants_admin_bootstrap_user') THEN
    ALTER TABLE identity.tenants
      ADD CONSTRAINT fk_tenants_admin_bootstrap_user
      FOREIGN KEY (admin_bootstrap_user_id) REFERENCES identity.users(user_id);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_tenants_status ON identity.tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_read_only ON identity.tenants(read_only);
CREATE INDEX IF NOT EXISTS idx_tenants_compliance_hold ON identity.tenants(compliance_hold);

CREATE TABLE IF NOT EXISTS identity.tenant_feature_flags (
  feature_flag_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES identity.tenants(tenant_id) ON DELETE CASCADE,
  flag_key varchar(120) NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  value_json jsonb NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_tenant_feature_flags UNIQUE (tenant_id, flag_key)
);
CREATE INDEX IF NOT EXISTS idx_tff_tenant_id ON identity.tenant_feature_flags(tenant_id);

CREATE TABLE IF NOT EXISTS identity.tenant_plan_limits (
  tenant_id uuid PRIMARY KEY REFERENCES identity.tenants(tenant_id) ON DELETE CASCADE,
  max_users int NOT NULL DEFAULT 5 CHECK (max_users >= 0),
  dsar_per_month int NOT NULL DEFAULT 50 CHECK (dsar_per_month >= 0),
  exports_per_month int NOT NULL DEFAULT 50 CHECK (exports_per_month >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS identity.tenant_monthly_usage (
  usage_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES identity.tenants(tenant_id) ON DELETE CASCADE,
  year_month int NOT NULL CHECK (year_month >= 200001 AND year_month <= 299912),
  dsar_count int NOT NULL DEFAULT 0 CHECK (dsar_count >= 0),
  export_count int NOT NULL DEFAULT 0 CHECK (export_count >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_tenant_month UNIQUE (tenant_id, year_month)
);
CREATE INDEX IF NOT EXISTS idx_tmu_tenant_month ON identity.tenant_monthly_usage(tenant_id, year_month);

CREATE TABLE IF NOT EXISTS identity.user_invites (
  invite_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES identity.tenants(tenant_id) ON DELETE CASCADE,
  email varchar(320) NOT NULL,
  roles_json jsonb NOT NULL,
  token_hash varchar(255) NOT NULL,
  token_hash_alg varchar(50) NOT NULL DEFAULT 'SHA256',
  expires_at timestamptz NOT NULL,
  used_at timestamptz NULL,
  used_by_user_id uuid NULL REFERENCES identity.users(user_id),
  created_by_user_id uuid NULL REFERENCES identity.users(user_id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_invites_active_email
  ON identity.user_invites(tenant_id, lower(email))
  WHERE used_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_invites_tenant_id ON identity.user_invites(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_invites_expires_at ON identity.user_invites(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_invites_token_hash ON identity.user_invites(token_hash);

ALTER TABLE identity.api_keys
  ADD COLUMN IF NOT EXISTS key_version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS revoked_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS rotated_from_api_key_id uuid NULL,
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
