-- Employee Data Compliance Domain Schema
-- DPDP-grade internal employee data management

CREATE SCHEMA IF NOT EXISTS employee;

-- 1) Employees (stub directory)
CREATE TABLE employee.employees (
    employee_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_ref TEXT NOT NULL,
    full_name TEXT NOT NULL,
    email TEXT,
    department TEXT,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_employee_ref UNIQUE (tenant_id, employee_ref)
);

CREATE INDEX idx_employees_tenant_status ON employee.employees(tenant_id, status);
CREATE INDEX idx_employees_tenant_created ON employee.employees(tenant_id, created_at DESC);

-- 2) HR Processing Purposes (inventory)
CREATE TABLE employee.hr_purposes (
    hr_purpose_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    purpose_key TEXT NOT NULL CHECK (purpose_key IN ('PAYROLL', 'BENEFITS', 'RECRUITMENT', 'PERFORMANCE', 'SECURITY', 'COMPLIANCE', 'OTHER')),
    description TEXT NOT NULL,
    lawful_basis TEXT NOT NULL CHECK (lawful_basis IN ('CONTRACT', 'LEGAL_OBLIGATION', 'CONSENT', 'LEGITIMATE_INTERESTS', 'OTHER')),
    retention_days INT CHECK (retention_days IS NULL OR retention_days >= 0),
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_purpose_key UNIQUE (tenant_id, purpose_key)
);

CREATE INDEX idx_hr_purposes_tenant ON employee.hr_purposes(tenant_id, created_at DESC);

-- 3) Employee Data Records (category tracking per employee)
CREATE TABLE employee.employee_data_records (
    record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL REFERENCES employee.employees(employee_id) ON DELETE CASCADE,
    data_category TEXT NOT NULL CHECK (data_category IN ('PII', 'FINANCIAL', 'HEALTH', 'BIOMETRIC', 'EMPLOYEE_DATA', 'DEVICE', 'OTHER')),
    hr_purpose_id UUID NOT NULL REFERENCES employee.hr_purposes(hr_purpose_id) ON DELETE RESTRICT,
    system_id UUID,  -- reference only to ROPA system
    notes TEXT,
    retention_days_override INT CHECK (retention_days_override IS NULL OR retention_days_override >= 0),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employee_data_records_tenant_employee ON employee.employee_data_records(tenant_id, employee_id);
CREATE INDEX idx_employee_data_records_tenant_category ON employee.employee_data_records(tenant_id, data_category);
CREATE INDEX idx_employee_data_records_tenant_purpose ON employee.employee_data_records(tenant_id, hr_purpose_id);
CREATE INDEX idx_employee_data_records_system ON employee.employee_data_records(system_id) WHERE system_id IS NOT NULL;

-- 4) Employee Rights Requests (DSAR-like internal)
CREATE TABLE employee.employee_requests (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL REFERENCES employee.employees(employee_id) ON DELETE CASCADE,
    request_type TEXT NOT NULL CHECK (request_type IN ('ACCESS', 'CORRECT', 'DELETE', 'WITHDRAW')),
    status TEXT NOT NULL CHECK (status IN ('RECEIVED', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CLOSED')),
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_to UUID,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ NOT NULL,
    sla_breached BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    closure_notes TEXT,
    evidence_bundle_id UUID
);

CREATE INDEX idx_employee_requests_tenant_status_due ON employee.employee_requests(tenant_id, status, due_at);
CREATE INDEX idx_employee_requests_tenant_employee ON employee.employee_requests(tenant_id, employee_id, created_at DESC);
CREATE INDEX idx_employee_requests_assigned ON employee.employee_requests(assigned_to) WHERE assigned_to IS NOT NULL;
CREATE INDEX idx_employee_requests_sla_breach ON employee.employee_requests(due_at, sla_breached) WHERE status != 'CLOSED' AND sla_breached = FALSE;
CREATE UNIQUE INDEX uq_employee_requests_idempotency ON employee.employee_requests(tenant_id, employee_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 5) Employee Request Status History (append-only audit trail)
CREATE TABLE employee.employee_request_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    request_id UUID NOT NULL REFERENCES employee.employee_requests(request_id) ON DELETE CASCADE,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by UUID,
    reason TEXT
);

CREATE INDEX idx_employee_request_history_tenant_request ON employee.employee_request_status_history(tenant_id, request_id, changed_at);

-- 6) Employee Exports (evidence bundles)
CREATE TABLE employee.employee_exports (
    export_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bundle_id UUID NOT NULL,
    evidence_export_id UUID NOT NULL,
    title TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employee_exports_tenant_created ON employee.employee_exports(tenant_id, created_at DESC);

-- Comments
COMMENT ON TABLE employee.employees IS 'Employee directory stub for linking compliance requests';
COMMENT ON TABLE employee.hr_purposes IS 'HR processing purposes inventory (payroll, benefits, etc.)';
COMMENT ON TABLE employee.employee_data_records IS 'Employee data category tracking per employee';
COMMENT ON TABLE employee.employee_requests IS 'Employee rights requests (access/correct/delete/withdraw) with SLA';
COMMENT ON TABLE employee.employee_request_status_history IS 'Append-only status change audit trail';
COMMENT ON TABLE employee.employee_exports IS 'Evidence export bundles for employee compliance reports';
