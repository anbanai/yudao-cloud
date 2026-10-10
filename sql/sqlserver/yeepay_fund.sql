-- 易宝资金与入网任务表首次安装，执行一次；保留所有现有数据。

-- 后续执行 yeepay_existing_payment_upgrade.sql；Oracle 需要 12.2+。

CREATE TABLE pay_fund_order (
    id bigint IDENTITY(1,1) NOT NULL,
    no nvarchar(64) NOT NULL,
    app_id bigint NOT NULL,
    channel_id bigint DEFAULT NULL,
    channel_code nvarchar(32) DEFAULT NULL,
    pay_order_id bigint DEFAULT NULL,
    pay_order_no nvarchar(64) DEFAULT NULL,
    merchant_order_id nvarchar(64) DEFAULT NULL,
    user_id bigint DEFAULT NULL,
    user_type smallint DEFAULT NULL,
    trade_order_id bigint DEFAULT NULL,
    trade_order_sub_id bigint DEFAULT NULL,
    divide_plan_data nvarchar(max),
    amount bigint NOT NULL DEFAULT 0,
    price bigint NOT NULL DEFAULT 0,
    fee_price bigint NOT NULL DEFAULT 0,
    divide_price bigint NOT NULL DEFAULT 0,
    refund_price bigint NOT NULL DEFAULT 0,
    clearing_status smallint NOT NULL DEFAULT 0,
    divide_status smallint NOT NULL DEFAULT 0,
    retry_count int NOT NULL DEFAULT 0,
    next_retry_time datetime2(6) DEFAULT NULL,
    last_error nvarchar(512) DEFAULT NULL,
    parent_merchant_no nvarchar(64) DEFAULT NULL,
    collection_merchant_no nvarchar(64) DEFAULT NULL,
    status smallint NOT NULL DEFAULT 0,
    currency nvarchar(8) NOT NULL DEFAULT 'CNY',
    notify_url nvarchar(1024) DEFAULT NULL,
    success_time datetime2(6) DEFAULT NULL,
    channel_order_no nvarchar(64) DEFAULT NULL,
    channel_request_no nvarchar(64) DEFAULT NULL,
    channel_error_code nvarchar(128) DEFAULT NULL,
    channel_error_msg nvarchar(256) DEFAULT NULL,
    channel_notify_data nvarchar(max),
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_fund_order_no ON pay_fund_order (no);

CREATE UNIQUE INDEX uk_pay_fund_order_pay_order ON pay_fund_order (pay_order_id) WHERE pay_order_id IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_fund_order_merchant ON pay_fund_order (app_id, merchant_order_id) WHERE merchant_order_id IS NOT NULL;

CREATE INDEX idx_pay_fund_order_pay_order ON pay_fund_order (pay_order_id);

CREATE INDEX idx_pay_fund_order_status ON pay_fund_order (status, id);

CREATE TABLE pay_fund_detail (
    id bigint IDENTITY(1,1) NOT NULL,
    no nvarchar(64) NOT NULL,
    fund_order_id bigint NOT NULL,
    fund_order_no nvarchar(64) DEFAULT NULL,
    pay_order_id bigint DEFAULT NULL,
    pay_order_no nvarchar(64) DEFAULT NULL,
    trade_order_id bigint DEFAULT NULL,
    trade_order_sub_id bigint DEFAULT NULL,
    trade_order_item_id bigint DEFAULT NULL,
    account_id bigint DEFAULT NULL,
    account_no nvarchar(64) DEFAULT NULL,
    merchant_id bigint DEFAULT NULL,
    merchant_no nvarchar(64) DEFAULT NULL,
    merchant_name nvarchar(128) DEFAULT NULL,
    divide_detail_no nvarchar(64) DEFAULT NULL,
    biz_type smallint DEFAULT NULL,
    direction smallint DEFAULT NULL,
    amount bigint NOT NULL DEFAULT 0,
    price bigint NOT NULL DEFAULT 0,
    fee_price bigint NOT NULL DEFAULT 0,
    divide_amount bigint DEFAULT NULL,
    refunded_amount bigint NOT NULL DEFAULT 0,
    returned_amount bigint NOT NULL DEFAULT 0,
    status smallint NOT NULL DEFAULT 0,
    currency nvarchar(8) NOT NULL DEFAULT 'CNY',
    success_time datetime2(6) DEFAULT NULL,
    channel_detail_no nvarchar(64) DEFAULT NULL,
    channel_error_code nvarchar(128) DEFAULT NULL,
    channel_error_msg nvarchar(256) DEFAULT NULL,
    remark nvarchar(255) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_fund_detail_no ON pay_fund_detail (no);

CREATE UNIQUE INDEX uk_pay_fund_detail_order_sub_order ON pay_fund_detail (fund_order_id, trade_order_sub_id) WHERE trade_order_sub_id IS NOT NULL;

CREATE INDEX idx_pay_fund_detail_order ON pay_fund_detail (fund_order_id);

CREATE INDEX idx_pay_fund_detail_account ON pay_fund_detail (account_no);

CREATE TABLE pay_divide_request (
    id bigint IDENTITY(1,1) NOT NULL,
    no nvarchar(64) NOT NULL,
    request_no nvarchar(64) DEFAULT NULL,
    app_id bigint NOT NULL,
    channel_id bigint DEFAULT NULL,
    channel_code nvarchar(32) DEFAULT NULL,
    fund_order_id bigint NOT NULL,
    fund_order_no nvarchar(64) DEFAULT NULL,
    fund_detail_id bigint DEFAULT NULL,
    fund_detail_no nvarchar(64) DEFAULT NULL,
    trade_order_id bigint DEFAULT NULL,
    trade_order_sub_id bigint DEFAULT NULL,
    merchant_id bigint DEFAULT NULL,
    divide_detail_no nvarchar(64) DEFAULT NULL,
    merchant_order_id nvarchar(64) DEFAULT NULL,
    merchant_request_no nvarchar(64) DEFAULT NULL,
    amount bigint NOT NULL DEFAULT 0,
    price bigint NOT NULL DEFAULT 0,
    status smallint NOT NULL DEFAULT 0,
    retry_count int NOT NULL DEFAULT 0,
    next_retry_time datetime2(6) DEFAULT NULL,
    channel_request_no nvarchar(64) DEFAULT NULL,
    request_data nvarchar(max),
    response_data nvarchar(max),
    channel_error_code nvarchar(128) DEFAULT NULL,
    channel_error_msg nvarchar(256) DEFAULT NULL,
    success_time datetime2(6) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_divide_request_no ON pay_divide_request (no);

CREATE UNIQUE INDEX uk_pay_divide_request_request_no ON pay_divide_request (request_no) WHERE request_no IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_divide_request_detail ON pay_divide_request (fund_detail_id) WHERE fund_detail_id IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_divide_request_channel ON pay_divide_request (channel_request_no) WHERE channel_request_no IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_divide_request_merchant ON pay_divide_request (app_id, merchant_request_no) WHERE merchant_request_no IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_divide_request_fund_order ON pay_divide_request (fund_order_id);

CREATE INDEX idx_pay_divide_request_status ON pay_divide_request (status, id);

CREATE TABLE pay_refund_fund_detail (
    id bigint IDENTITY(1,1) NOT NULL,
    no nvarchar(64) NOT NULL,
    refund_id bigint DEFAULT NULL,
    refund_no nvarchar(64) DEFAULT NULL,
    pay_order_id bigint DEFAULT NULL,
    pay_order_no nvarchar(64) DEFAULT NULL,
    fund_order_id bigint NOT NULL,
    fund_order_no nvarchar(64) DEFAULT NULL,
    fund_detail_id bigint DEFAULT NULL,
    fund_detail_no nvarchar(64) DEFAULT NULL,
    after_sale_id bigint DEFAULT NULL,
    merchant_id bigint DEFAULT NULL,
    divide_detail_no nvarchar(64) DEFAULT NULL,
    divide_back_request_id nvarchar(128) DEFAULT NULL,
    app_id bigint NOT NULL,
    channel_id bigint DEFAULT NULL,
    channel_code nvarchar(32) DEFAULT NULL,
    merchant_refund_id nvarchar(64) DEFAULT NULL,
    amount bigint NOT NULL DEFAULT 0,
    price bigint NOT NULL DEFAULT 0,
    refund_status smallint NOT NULL DEFAULT 0,
    divide_back_status smallint NOT NULL DEFAULT 10,
    status smallint NOT NULL DEFAULT 0,
    reason nvarchar(255) DEFAULT NULL,
    channel_refund_no nvarchar(64) DEFAULT NULL,
    channel_error_code nvarchar(128) DEFAULT NULL,
    channel_error_msg nvarchar(256) DEFAULT NULL,
    channel_notify_data nvarchar(max),
    retry_count int NOT NULL DEFAULT 0,
    next_retry_time datetime2(6) DEFAULT NULL,
    last_error nvarchar(512) DEFAULT NULL,
    divide_back_success_time datetime2(6) DEFAULT NULL,
    success_time datetime2(6) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_no ON pay_refund_fund_detail (no);

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_refund_detail ON pay_refund_fund_detail (refund_id, fund_detail_id) WHERE refund_id IS NOT NULL AND fund_detail_id IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_refund_after_sale ON pay_refund_fund_detail (refund_id, after_sale_id) WHERE refund_id IS NOT NULL AND after_sale_id IS NOT NULL;

CREATE INDEX idx_pay_refund_fund_detail_refund ON pay_refund_fund_detail (refund_id);

CREATE INDEX idx_pay_refund_fund_detail_order ON pay_refund_fund_detail (fund_order_id);

CREATE TABLE pay_yeepay_merchant_account (
    id bigint IDENTITY(1,1) NOT NULL,
    app_id bigint NOT NULL,
    merchant_id bigint DEFAULT NULL,
    merchant_no nvarchar(64) DEFAULT NULL,
    request_no nvarchar(128) DEFAULT NULL,
    application_no nvarchar(128) DEFAULT NULL,
    sign_url nvarchar(1024) DEFAULT NULL,
    auth_url nvarchar(1024) DEFAULT NULL,
    materials_ref nvarchar(255) DEFAULT NULL,
    materials_version int DEFAULT NULL,
    submission_token nvarchar(64) DEFAULT NULL,
    submission_expire_time datetime2(6) DEFAULT NULL,
    last_request_time datetime2(6) DEFAULT NULL,
    last_error nvarchar(512) DEFAULT NULL,
    merchant_name nvarchar(128) DEFAULT NULL,
    account_no nvarchar(64) DEFAULT NULL,
    account_name nvarchar(128) DEFAULT NULL,
    account_type smallint DEFAULT NULL,
    onboarding_status smallint NOT NULL DEFAULT 0,
    status smallint NOT NULL DEFAULT 0,
    bank_name nvarchar(128) DEFAULT NULL,
    bank_account_no nvarchar(128) DEFAULT NULL,
    contact_name nvarchar(64) DEFAULT NULL,
    contact_phone nvarchar(32) DEFAULT NULL,
    channel_data nvarchar(max),
    remark nvarchar(255) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_yeepay_account_merchant ON pay_yeepay_merchant_account (app_id, merchant_id, deleted) WHERE merchant_id IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_yeepay_account_merchant_no ON pay_yeepay_merchant_account (app_id, merchant_no, deleted) WHERE merchant_no IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_yeepay_account_request ON pay_yeepay_merchant_account (app_id, request_no) WHERE request_no IS NOT NULL;

CREATE UNIQUE INDEX uk_pay_yeepay_account_application ON pay_yeepay_merchant_account (app_id, application_no) WHERE application_no IS NOT NULL;

CREATE INDEX idx_pay_yeepay_account_no ON pay_yeepay_merchant_account (account_no);

CREATE TABLE pay_yeepay_event (
    id bigint IDENTITY(1,1) NOT NULL,
    event_id nvarchar(128) NOT NULL,
    event_no nvarchar(128) DEFAULT NULL,
    event_type nvarchar(64) NOT NULL,
    merchant_no nvarchar(64) DEFAULT NULL,
    request_id nvarchar(128) DEFAULT NULL,
    fund_order_no nvarchar(64) DEFAULT NULL,
    payload nvarchar(max),
    raw_data_ref nvarchar(255) DEFAULT NULL,
    encrypted_original nvarchar(max) DEFAULT NULL,
    decrypted_summary nvarchar(max),
    status smallint NOT NULL DEFAULT 0,
    ack_status smallint NOT NULL DEFAULT 0,
    retry_count int NOT NULL DEFAULT 0,
    last_error nvarchar(512) DEFAULT NULL,
    event_time datetime2(6) DEFAULT NULL,
    processed_time datetime2(6) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_yeepay_event_id ON pay_yeepay_event (event_id);

CREATE INDEX idx_pay_yeepay_event_status ON pay_yeepay_event (status, id);

CREATE TABLE pay_reconciliation_record (
    id bigint IDENTITY(1,1) NOT NULL,
    batch_no nvarchar(64) NOT NULL,
    app_id bigint NOT NULL,
    channel_id bigint DEFAULT NULL,
    merchant_no nvarchar(64) DEFAULT NULL,
    channel_code nvarchar(32) DEFAULT NULL,
    reconciliation_date date NOT NULL,
    total_count int NOT NULL DEFAULT 0,
    total_amount bigint NOT NULL DEFAULT 0,
    total_price bigint NOT NULL DEFAULT 0,
    matched_count int NOT NULL DEFAULT 0,
    matched_amount bigint NOT NULL DEFAULT 0,
    difference_amount bigint NOT NULL DEFAULT 0,
    status smallint NOT NULL DEFAULT 0,
    file_url nvarchar(1024) DEFAULT NULL,
    raw_data nvarchar(max),
    error_msg nvarchar(512) DEFAULT NULL,
    started_time datetime2(6) DEFAULT NULL,
    completed_time datetime2(6) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_reconciliation_batch_no ON pay_reconciliation_record (batch_no);

CREATE INDEX idx_pay_reconciliation_merchant_date ON pay_reconciliation_record (merchant_no, reconciliation_date);

CREATE TABLE pay_fund_operation (
    id bigint IDENTITY(1,1) NOT NULL,
    action nvarchar(64) NOT NULL,
    target_id bigint NOT NULL,
    operator_id bigint NOT NULL,
    reason nvarchar(512) NOT NULL,
    external_evidence nvarchar(1024) NOT NULL,
    previous_state nvarchar(max) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_fund_operation_target ON pay_fund_operation (tenant_id, action, target_id);

CREATE TABLE pay_reconciliation_difference (
    id bigint IDENTITY(1,1) NOT NULL,
    record_id bigint NOT NULL,
    difference_key nvarchar(64) NOT NULL,
    biz_type nvarchar(32) NOT NULL,
    request_no nvarchar(64) DEFAULT NULL,
    order_no nvarchar(64) DEFAULT NULL,
    merchant_no nvarchar(64) DEFAULT NULL,
    detail_no nvarchar(64) DEFAULT NULL,
    local_amount bigint DEFAULT NULL,
    channel_amount bigint DEFAULT NULL,
    difference_amount bigint DEFAULT NULL,
    reason nvarchar(1000) NOT NULL,
    status smallint NOT NULL DEFAULT 0,
    operator_id bigint DEFAULT NULL,
    resolution_reason nvarchar(1000) DEFAULT NULL,
    external_evidence nvarchar(1000) DEFAULT NULL,
    resolved_time datetime2(6) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_reconciliation_difference ON pay_reconciliation_difference (difference_key);

CREATE INDEX idx_reconciliation_record ON pay_reconciliation_difference (record_id, status);

CREATE TABLE product_merchant_pay_onboarding (
    id bigint IDENTITY(1,1) NOT NULL  PRIMARY KEY,
    merchant_id bigint NOT NULL,
    merchant_approval_time datetime2(6) NOT NULL,
    app_key nvarchar(64) NOT NULL,
    request_no nvarchar(64) NOT NULL,
    business_role nvarchar(32) NOT NULL,
    state nvarchar(32) NOT NULL,
    materials_json nvarchar(max) DEFAULT NULL,
    materials_version int NOT NULL DEFAULT 0,
    attempt_count int NOT NULL DEFAULT 0,
    request_accepted bit NOT NULL DEFAULT 0,
    next_retry_time datetime2(6) DEFAULT NULL,
    last_error nvarchar(128) DEFAULT NULL,
    onboarding_status int DEFAULT NULL,
    sign_url nvarchar(max) DEFAULT NULL,
    auth_url nvarchar(max) DEFAULT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_merchant_approval ON product_merchant_pay_onboarding (tenant_id, merchant_id, merchant_approval_time);

CREATE INDEX idx_due ON product_merchant_pay_onboarding (state, next_retry_time, tenant_id);

CREATE TABLE product_merchant_pay_material_revision (
    id bigint IDENTITY(1,1) NOT NULL  PRIMARY KEY,
    onboarding_id bigint NOT NULL,
    merchant_id bigint NOT NULL,
    materials_version int NOT NULL,
    business_role nvarchar(32) NOT NULL,
    operator_id bigint DEFAULT NULL,
    materials_json nvarchar(max) NOT NULL,
    creator nvarchar(64) DEFAULT '',
    create_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) DEFAULT '',
    update_time datetime2(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL DEFAULT 0,
    tenant_id bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_material_revision ON product_merchant_pay_material_revision (tenant_id, onboarding_id, materials_version);
