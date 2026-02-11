# Round 2 Final Checklist (Children Guardian)

## Tests
- [ ] Part 1–4 tests green (children-guardian-service)
- [ ] Full children-guardian-service suite green

## Backward Compatibility
- [ ] No endpoint removals or breaking changes
- [ ] Existing migrations untouched

## Majority Transition
- [ ] Majority transitions are idempotent per child + majorityDate
- [ ] Guardian authority revoked when majority reached
- [ ] New consents blocked after authority revoked (409 ADULT_CONSENT_REQUIRED)

## Notifications
- [ ] Guardians notified on transition (no hard dependency; failures tolerated)
- [ ] Notification attempts recorded in transition row

## Evidence Bundle Export
- [ ] Evidence export is idempotent by tenant + child + scope + idempotency key
- [ ] Evidence bundle ref + sha stored and audit/outbox emitted
- [ ] Failure path records last error and returns 503

## Audit + Outbox Coverage
- [ ] `child.majority_threshold_reached`
- [ ] `child.guardian_authority_revoked`
- [ ] `child.adult_consent_required`
- [ ] `child.evidence_bundle_created`
- [ ] Canonical consent audit mapping documented: `consent.approved` ⇒ GUARDIAN_CONSENT_GRANTED, `consent.revoked` ⇒ GUARDIAN_CONSENT_REVOKED

## Event Name Mapping Table (Spec → Implemented → Location)

| Spec name | Implemented audit/outbox name(s) | Location |
| --- | --- | --- |
| GUARDIAN_CONSENT_GRANTED | `consent.approved` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/ConsentService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/ConsentService.java) |
| GUARDIAN_CONSENT_REVOKED | `consent.revoked` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/ConsentService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/ConsentService.java) |
| CHILD_MAJORITY_THRESHOLD_REACHED | `CHILD_MAJORITY_THRESHOLD_REACHED` / `child.majority_threshold_reached` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java) |
| GUARDIAN_AUTHORITY_REVOKED_DUE_TO_MAJORITY | `GUARDIAN_AUTHORITY_REVOKED_DUE_TO_MAJORITY` / `child.guardian_authority_revoked` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java) |
| ADULT_CONSENT_REQUIRED | `ADULT_CONSENT_REQUIRED` / `child.adult_consent_required` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/MajorityService.java) |
| CHILD_EVIDENCE_BUNDLE_CREATED | `CHILD_EVIDENCE_BUNDLE_CREATED` / `child.evidence_bundle_created` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EvidenceExportService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EvidenceExportService.java) |
| ESIGN_REQUEST_CREATED | `ESIGN_REQUEST_CREATED` / `esign.request_created` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignRequestService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignRequestService.java) |
| ESIGN_WEBHOOK_RECEIVED | `ESIGN_WEBHOOK_RECEIVED` / `esign.webhook_received` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java) |
| ESIGN_SIGNATURE_VERIFIED | `ESIGN_SIGNATURE_VERIFIED` / `esign.signature_verified` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java) |
| ESIGN_SIGNATURE_FAILED | `ESIGN_SIGNATURE_FAILED` / `esign.signature_failed` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java) |
| GUARDIAN_SIGNED_ARTIFACT_STORED | `GUARDIAN_SIGNED_ARTIFACT_STORED` / `guardian.signed_artifact_stored` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/EsignWebhookService.java) |
| AGE_RULE_CREATED | `AGE_RULE_CREATED` / `age_rule.created` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java) |
| AGE_RULE_UPDATED | `AGE_RULE_UPDATED` / `age_rule.updated` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java) |
| CHILD_AGE_EVALUATED | `CHILD_AGE_EVALUATED` / `child.age_evaluated` | [services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java](services/children-guardian-service/src/main/java/com/regulyn/guardian/service/AgeRuleService.java) |

## Final
- [ ] All new endpoints documented
- [ ] No Round 1 regressions
