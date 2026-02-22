-- V6: Convert CHAR(64) columns to VARCHAR(64) so Hibernate schema validation passes.
-- PostgreSQL stores CHAR(n) as bpchar (Types#CHAR) but Hibernate's PostgreSQL dialect
-- validates all String mappings against VARCHAR (Types#VARCHAR), causing startup failure.

ALTER TABLE scanner.scanned_pages
    ALTER COLUMN url_hash          TYPE VARCHAR(64),
    ALTER COLUMN page_summary_hash TYPE VARCHAR(64);

ALTER TABLE scanner.scan_findings
    ALTER COLUMN finding_fingerprint TYPE VARCHAR(64);

ALTER TABLE scanner.remediation_tasks
    ALTER COLUMN finding_fingerprint TYPE VARCHAR(64),
    ALTER COLUMN closure_notes_hash  TYPE VARCHAR(64);

ALTER TABLE scanner.scan_run_evidence_refs
    ALTER COLUMN bundle_hash TYPE VARCHAR(64);
