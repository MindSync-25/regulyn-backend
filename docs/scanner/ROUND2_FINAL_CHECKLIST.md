# Round 2 Final Checklist

## Tests
- [ ] Part 1–4 tests green (scanner-service)
- [ ] Full scanner-service suite green

## Backward Compatibility
- [ ] No endpoint removals or breaking changes
- [ ] Existing migrations untouched

## Audit + Outbox Coverage
- [ ] Task generation: `scanner.task_created_from_finding`
- [ ] Task status changes: `scanner.task_status_changed`
- [ ] Task evidence artifact stored: `scanner.task_evidence_artifact_stored`
- [ ] Run evidence bundle created: `scanner.scan_evidence_bundle_created`

## Evidence
- [ ] Task close/waive generates evidence artifact ref (idempotent)
- [ ] Run evidence bundle stored in `scan_run_evidence_refs` (idempotent)
- [ ] Bundle hash recorded and reproducible

## PARTIAL Safety
- [ ] Bundle allowed for PARTIAL runs
- [ ] No run status mutation on bundle creation

## Final
- [ ] All new endpoints documented
- [ ] No Round 1 regressions
