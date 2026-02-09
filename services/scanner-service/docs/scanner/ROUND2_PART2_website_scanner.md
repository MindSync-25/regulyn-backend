# Scanner Service — Round 2 — Part 2 (Website Scanner)

## Overview
Part 2 adds the WEBSITE adapter and a safe crawl engine to scan websites for cookies, forms, trackers, and external endpoints. It persists page summaries to `scanner.scanned_pages` and normalized findings to `scanner.scan_findings`. No remediation tasks are created in Part 2.

## Adapter Configuration
The WEBSITE adapter is configured via `scan_sources.base_url` and `scan_sources.metadata`.

### Start URLs
- Prefer `metadata.startUrls` when present (list of URLs).
- Fallback to `base_url` if `startUrls` is empty.

### Crawl Settings (metadata)
```json
{
  "maxDepth": 2,
  "maxPages": 50,
  "perRequestTimeoutMs": 3000,
  "totalTimeoutMs": 20000,
  "userAgent": "RegulynScanner/2.0",
  "allowExternalDomains": false,
  "respectRobots": false,
  "maxFailures": 10
}
```

## Fail-Safe Behavior
- The crawl enforces **total timeout** and **per-request timeout**.
- If partial progress occurs and a limit is reached, the run status is set to `PARTIAL`.
- Partial runs keep all persisted pages and findings and include a structured reason in `scan_runs.error_message`.

## Persistence

### scanned_pages
Per-page summary only (no HTML storage). Key fields:
- `url`, `url_hash`, `depth`, `http_status`, `content_type`, `title`, `duration_ms`
- `cookie_count`, `form_count`, `tracker_count`, `has_pii_form_fields`
- `data_collection_summary` (JSONB) with capped lists
- `page_summary_hash` (sha256 of summary JSON)
- `sample_text` (first ~300 chars of visible text)

### scan_findings
Website findings are stored in `scan_findings` with:
- `finding_type` = `FIELD`
- `entity_type` = `WEB_PAGE`
- `normalized_subject` = normalized page URL
- `finding_fingerprint` + `key_attributes` for idempotency
- `details.websiteFindingKind` indicates the website-specific finding kind

## Findings Created in Part 2
Objective detections only:
- `COOKIE_PRESENT`
- `FORM_PII_DETECTED`
- `TRACKER_DETECTED`
- `INSECURE_FORM_ACTION_HTTP`
- `UNKNOWN_THIRD_PARTY_ENDPOINT`

Future kinds like consent gating are deferred to Part 3.

## Events (Audit + Outbox)
- `scanner.run_started`
- `scanner.run_succeeded`
- `scanner.run_failed`
- `scanner.run_partial` (new)
- `scanner.finding_detected` (new batch event)

## Not Included
- Remediation task creation (Part 3)
- Evidence service integration for tasks (Part 4)