-- Reviewed migration only. Never run automatically on application startup.
-- All tables reside in the member database; no credentials / raw phone / login or handoff codes.
CREATE TABLE member_identity_scope (
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  ready TINYINT NOT NULL DEFAULT 0,
  audited_at BIGINT NULL,
  audited_by VARCHAR(128) NULL
);
CREATE TABLE member_identity_account (
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  subject_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  linked TINYINT NOT NULL DEFAULT 0,
  phone_sharing TINYINT NOT NULL DEFAULT 0,
  phone_consent_version VARCHAR(64) NULL,
  phone_consent_revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  phone_consented_at BIGINT NULL,
  phone_cipher TEXT NULL,
  phone_method VARCHAR(16) NULL,
  verified_at BIGINT NULL,
  phone_version BIGINT NOT NULL DEFAULT 0,
  origin_version BIGINT NOT NULL DEFAULT 0,
  center_phone_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(scope,openid),
  UNIQUE KEY uk_identity_member(scope,member_id),
  UNIQUE KEY uk_identity_subject(scope,subject_id)
);
CREATE TABLE member_identity_operation (
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  handoff_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY(scope,request_id),
  UNIQUE KEY uk_identity_handoff(scope,handoff_id)
);
CREATE TABLE member_identity_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  version BIGINT NOT NULL,
  verification_version BIGINT NOT NULL,
  share_consent TINYINT NOT NULL,
  payload_cipher TEXT NULL,
  state VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt BIGINT NOT NULL,
  UNIQUE KEY uk_identity_event(scope,event_id),
  UNIQUE KEY uk_identity_origin_version(scope,member_id,version),
  KEY ix_identity_dispatch(scope,state,next_attempt)
);
-- Complete, frozen inventory exported from system_social_user + system_social_user_bind.
-- The old schema has no authoritative app ID: NEVER infer it from openid or current credentials.
CREATE TABLE member_identity_legacy_review (
  source_bind_id BIGINT PRIMARY KEY,
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NULL,
  openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  verdict VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED',
  evidence VARCHAR(512) NULL
);

CREATE TABLE member_identity_consent (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scope VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  consent_revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  consent_text TEXT NOT NULL,
  operator_name VARCHAR(256) NOT NULL,
  privacy_url VARCHAR(1024) NOT NULL,
  created_at BIGINT NOT NULL,
  KEY ix_identity_consent(scope,member_id,created_at)
);
