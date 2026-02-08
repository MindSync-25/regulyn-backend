# Consent Service Deployment Checklist (Round 2)

## Database
- [ ] Flyway migrations applied (V1–V6).
- [ ] Index health verified (consent_receipts, purpose_versions, communication_consent_ledger).
- [ ] Vacuum/autovacuum settings reviewed for audit/outbox growth.

## Secrets / Config
- [ ] Translation engine configured (if not Noop).
  - [ ] Rate limits set and monitored.
- [ ] Evidence service URL + auth configured (if enabled).

## Observability
- [ ] Metrics wired and alerting for:
  - [ ] Translation generation count/failures.
  - [ ] Purpose widening detections count.
  - [ ] Invalidations + reconsent required count.
  - [ ] Communication opt-in/out counts.
  - [ ] Fail-closed grant rejects count.

## Reliability
- [ ] Translation/evidence retries are bounded.
- [ ] Idempotency behavior verified for grants and comm ledger.

## Data Retention
- [ ] audit_events retention policy defined.
- [ ] outbox_events retention/compaction policy defined.

## Security
- [ ] No raw content stored in audit/outbox payloads.
- [ ] PII not logged.

## Regulator Readiness
- [ ] Ability to locate consent proof by receiptId + hashes.
- [ ] Evidence artifact references retrievable.
- [ ] Ability to show “what language was shown” via english/regional snapshot fields.
