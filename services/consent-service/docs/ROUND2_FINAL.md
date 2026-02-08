# Round 2 Final Summary (Parts 2–4)

## 1) What Round 2 Adds
- Purpose versioning with widening detection and invalidation/reconsent enforcement.
- Communication consent ledger with idempotent opt-in/out and batch status.
- Dual-language consent snapshots (English + 1 regional) tied to receipts.
- Auto-translation generation for missing regional notice language rows.
- Fail-closed behavior when required artifacts are missing or unavailable.

## 2) India Language Rule
Consent UI must show English plus exactly one regional language based on region. There is no user language selection.

## 3) Region Mapping (State/UT → Language Code)
- KA → kn
- TN → ta
- TS / AP → te
- KL → ml
- MH → mr
- WB → bn
- GJ → gu
- PB → pa
- OD / OR → or
- AS → as
- Default → hi

## 4) Fail-Closed Rules
- No purpose version for a publish/grant → grant rejected.
- Widened purpose → invalidate prior receipts + require reconsent.
- Translation unavailable → active-dual and region-based grant blocked.
- Unknown communication consent → deny (allowed=false).

## 5) Evidence/Audit/Outbox Coverage (Event Names)
- PURPOSE_VERSION_CREATED
- PURPOSE_WIDENED_DETECTED
- CONSENT_INVALIDATED_DUE_TO_PURPOSE_CHANGE
- RECONSENT_REQUIRED
- RECONSENT_SATISFIED
- COMMUNICATION_OPT_IN / COMMUNICATION_OPT_OUT
- CONSENT_LANGUAGE_TRANSLATION_GENERATED
- CONSENT_LANGUAGE_SNAPSHOT_STORED
- consent.granted / consent.withdrawn (Round 1)

**Note:** Event payloads contain IDs + hashes only (no raw content).

## 6) How to Run Tests
- From services/consent-service: `mvn test`
- All integration tests use Testcontainers with real PostgreSQL.
