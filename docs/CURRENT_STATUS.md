# Current engineering status — 27 October 2026

This is the entry point for current engineering status. Dated documents are
evidence of what was verified then, not live configuration queries.

- The publisher-approved referral activation is recorded in
  [REFERRAL_ACTIVATION_STATUS.md](REFERRAL_ACTIVATION_STATUS.md), including the
  actual deployment run, campaign dates and minimum Play version 10.
- That record explicitly distinguishes publisher-reported genuine acceptance
  from the agent's mocked/backend/isolated QA evidence.
- Read-only Play Console recheck on 27 October confirmed version 10 internal
  (available to testers) and version 9 production candidate still in review.
  This is not proof of a public version 10 release. No audit fix
  authorizes editing/replacing the existing in-review submission.
- A source commit does not prove a Worker deployment. Use the latest successful
  manually dispatched `deploy-cloudflare.yml` run and its source SHA to identify
  deployed backend code. Android signed release is a different workflow.
- `/ready` reports configuration readiness. `/health/operations` additionally
  checks referral backlog/provider trouble and refund reconciliation age.
  Neither endpoint proves a positive Play-installed real-device referral.
- The next-update hardening changes, measured evidence and remaining external
  acceptance boundaries are in [AUDIT_HARDENING.md](AUDIT_HARDENING.md).

The Android release workflow now requires complete CI, including explicitly
requested isolated emulator instrumentation, on the exact signed commit.
It rejects a version below an active campaign's minimum. Signing does not
publish/upload to Play; no workflow added by this hardening changes Play tracks.

Engineering checks are not Google policy certification or legal approval in
every country. Do not reinterpret SDK verdicts as a unique-human fingerprint.
