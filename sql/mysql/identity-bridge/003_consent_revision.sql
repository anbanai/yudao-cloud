-- Only for installations that applied the earlier 001_schema.sql without these columns.
-- Fresh installs use the updated 001_schema.sql and MUST NOT run this upgrade.
-- Apply with bridge traffic paused. Keep historical receipts unchanged; NULL revisions
-- invalidate earlier sharing and the retry job durably withdraws it in bounded batches.
ALTER TABLE member_identity_account
  ADD COLUMN phone_consent_revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER phone_consent_version;
ALTER TABLE member_identity_consent
  ADD COLUMN consent_revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER policy_version;
