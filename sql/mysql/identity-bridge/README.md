# Mall identity bridge rollout (not executed)

The feature defaults off. Deploy system-api/system-server and member-server together. Use Java 17. Fresh installations use the current 001_schema.sql. If an earlier bridge schema was already applied without consent revision columns, review and apply 003_consent_revision.sql with bridge traffic paused; do not run that upgrade after the current fresh schema. Historical NULL revisions intentionally require renewed consent and are withdrawn by the retry job.
Apply 001_schema.sql in the member DB. During a login write freeze export the complete historical mini-program social-binding inventory from the system DB; retain an immutable export and source count. Review the actual app ID and tenant for each binding using deployment evidence. Populate member_identity_legacy_review; do not guess app ID, combine phones, delete social binds, or mark imported mobiles verified. Apply the procedure in 002_reviewed_backfill.sql and invoke with the independently reconciled source export count. Unresolved ownership or conflicting member/OpenID rows block readiness. New installations also require an explicit reviewed zero-inventory readiness entry. Only resume mini-program traffic after readiness is recorded. Do not revert to legacy login while bridge accounts exist; rollback means disable navigation issuing and keep scoped B authentication deployed.

Inject configuration through environment/secrets or Nacos (never the public navigation catalog):

```yaml
yudao:
  identity-bridge:
    enabled: false
    center-url: https://identity.example.invalid
    allow-local-http: false # only loopback + local/test profile, never prod/production
    app-id: ${MALL_WECHAT_APP_ID}
    app-secret: ${MALL_WECHAT_APP_SECRET}
    backend-instance: ${MALL_BACKEND_INSTANCE}
    tenant-id: ${MALL_TENANT_ID}
    key-id: ${IDENTITY_KEY_ID}
    hmac-secret: ${IDENTITY_HMAC_SECRET} # base64 >=32-byte key, identical encoded value at center
    encryption-key: ${IDENTITY_ENCRYPTION_KEY} # base64 32-byte AES key
    source-app-ids: ${IDENTITY_SOURCE_APP_IDS}
    identity-operator-name: ${IDENTITY_OPERATOR_NAME}
    phone-sharing-consent-text: ${IDENTITY_PHONE_CONSENT_TEXT}
    phone-sharing-privacy-url: ${IDENTITY_PRIVACY_URL} # HTTPS
    phone-sharing-policy-version: ${IDENTITY_PHONE_POLICY_VERSION}
```

The center must register key ID to exactly this B app, backend instance and tenant. The Java backend rejects a request tenant different from its configured tenant. A single deployment supports any number of server-configured tenants through the registry below. Unknown tenants are bridge-disabled; incoming app IDs or source claims cannot choose credentials. AES-GCM encrypts the additional provenance and outbox payload; member_user.mobile follows the existing member schema and must receive normal database encryption/access controls. Never log HTTP request bodies for bridge-login, center identity requests, WeChat URLs, phone endpoints or auth endpoints; disable request-body collection in the gateway/APM for these paths. Credential fields intentionally have no toString.

Before enabling set system service `yudao.sms-code.begin-code=100000`, `end-code=999999`, `expire-times<=10m` with actual SMS delivery, existing frequency/day limits, and anti-abuse controls. SMS generation now uses SecureRandom. The member bridge checks the system service's authenticated internal verification-safety RPC before all enabled bridge/ordinary mini login and SMS login/changes. Example 9999 configs fail closed. This prerequisite prevents imported verified phones from becoming fixed-code login targets. Existing SMS remains untouched while the bridge is disabled. SMS provenance is recorded only after successful scene-specific useSmsCode; passwords, admin/import changes and shipping addresses are not proof.

The normal response includes B accessToken/refreshToken/openid/userId. Do not auto-install bridge responses globally. Fresh B WeChat exchange is required even on retry. Retry the same request ID and handoff with a fresh B code after center timeouts. The local CLAIMED reservation commits before remote complete; completed center responses are safe to replay. Local reservations are durable and intentionally never automatically reassigned. Expired unresolved reservations need manual audited support; no background guessing or merging. Member conflict is numeric 1004019001 / IDENTITY_CONFLICT; temporary/configuration failure is 1004019002.

GET /member/identity/status refreshes known identity from center. POST /member/identity/phone-sharing accepts `{enabled,consentRevision}`. Enabling requires the exact `phoneSharingConsentRevision` returned alongside the displayed status disclosure; missing/stale revisions return 1004019001 without accepting new terms. Withdrawal accepts a missing or stale revision. No phone is sent until consent is true, local verification provenance exists, and center says binding remains active. Local revocation cancels pending sends. A 30-second job retries encrypted outbox events with bounded exponential backoff; inspect PENDING/attempts and FAILED requests operationally (no PII logs). Center also enforces current grant on each send. Shared center phone is trusted proof for B membership and is stored in member_user.mobile; no repeat phone authorization is needed. Conflicting phones or other members already owning that phone are rejected without a merge. Local proof and remote proof are distinguished; imported center data is never echoed back as a new local verification event.

Real WeChat, HMAC center and database rollout smoke tests remain required before enabling. Unit tests exercise protocol crypto, application state machine and persistence with H2 and mocked external WeChat/center/member infrastructure; no production DB is mutated by the implementation process.

Consent changes are appended to member_identity_consent with the exact displayed operator, purpose text, privacy URL, policy version and derived disclosure revision. The revision binds these fields and tenant/app/backend scope using SHA-256 over an unambiguous encoding. Changing any disclosure field invalidates earlier local phone-sharing consent even when the configured policy version is unchanged, until the user explicitly accepts the new disclosure again. Each tenant's retry tick reconciles up to 50 expired consents, including previously sent opt-ins for dormant members, and durably queues their withdrawal before dispatch. A center outage delays remote withdrawal until retry succeeds. Scope keys include tenant, app and backend instance.


## Multi-tenant registry in one deployment

For multiple malls, set the top-level `enabled` master switch and configure `yudao.identity-bridge.tenants.<tenant-id>` in Nacos/server configuration. Each entry contains all scope fields shown above, including its own `enabled`, `app-id`, `app-secret`, `backend-instance`, center URL, HMAC/key ID, AES key, source allowlist and disclosure fields. The map key is the trusted tenant ID; an optional conflicting tenant-id inside an entry rejects configuration. Example shape (substitute secrets through your configuration provider):

```yaml
yudao:
  identity-bridge:
    enabled: true
    tenants:
      1:
        enabled: true
        app-id: ${MALL_ONE_APP_ID}
        app-secret: ${MALL_ONE_APP_SECRET}
        # ... all remaining scope fields from the single-scope example
      2:
        enabled: true
        app-id: ${MALL_TWO_APP_ID}
        app-secret: ${MALL_TWO_APP_SECRET}
        # ... distinct app/key/AES/disclosure settings
```

The existing appId-to-tenant discovery remains authoritative for selecting the tenant; the bridge reads TenantContextHolder and resolves only an explicitly configured registry entry. Add another mall by publishing the directory and adding its tenant registry entry and reviewed migration scope, without code changes or a separate deployment. The outbox scheduler iterates configured enabled tenants and restores the previous tenant context. Legacy single-scope settings remain supported when the registry is empty.

Phone proof version and outgoing event version are separate. Explicit opt-out always creates a new durable event with `share_consent:false`, even after a previous opt-in was already sent and without requiring new phone proof. It cancels prior pending opt-ins, survives a center outage, and is retried without a current grant. The center removes that origin's phone visibility for A and other recipients. Independent locally verified B data is retained. If a resolve response withdraws phone/identity scope, B deletes only center-derived live phone cache/member.mobile and retains independent WeChat/SMS verification. Center unavailability likewise hides center-only data while allowing ordinary B authentication with freshly validated B OpenID and the same membership. New bridge redemption still requires successful center claim/complete.

Interop validation: `IdentityInteropTest` is opt-in using `IDENTITY_INTEROP_FIXTURE_FILE` pointing to a loopback Go router fixture with a synthetic SQLite DB. It exercises real signed Java-to-Go claim/complete/resolve, phone sharing and withdrawal, A grant revocation and target disable. Do not point fixtures at deployed services.
