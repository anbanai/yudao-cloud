-- 易宝资金与入网任务表首次安装，执行一次；保留所有现有数据。

-- 后续执行 yeepay_existing_payment_upgrade.sql；Oracle 需要 12.2+。

CREATE SEQUENCE pay_fund_order_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_fund_order (
    id number(19) NOT NULL,
    no varchar2(64) NOT NULL,
    app_id number(19) NOT NULL,
    channel_id number(19) DEFAULT NULL,
    channel_code varchar2(32) DEFAULT NULL,
    pay_order_id number(19) DEFAULT NULL,
    pay_order_no varchar2(64) DEFAULT NULL,
    merchant_order_id varchar2(64) DEFAULT NULL,
    user_id number(19) DEFAULT NULL,
    user_type number(10) DEFAULT NULL,
    trade_order_id number(19) DEFAULT NULL,
    trade_order_sub_id number(19) DEFAULT NULL,
    divide_plan_data clob,
    amount number(19) DEFAULT 0 NOT NULL,
    price number(19) DEFAULT 0 NOT NULL,
    fee_price number(19) DEFAULT 0 NOT NULL,
    divide_price number(19) DEFAULT 0 NOT NULL,
    refund_price number(19) DEFAULT 0 NOT NULL,
    clearing_status number(10) DEFAULT 0 NOT NULL,
    divide_status number(10) DEFAULT 0 NOT NULL,
    retry_count number(10) DEFAULT 0 NOT NULL,
    next_retry_time timestamp(6) DEFAULT NULL,
    last_error varchar2(512) DEFAULT NULL,
    parent_merchant_no varchar2(64) DEFAULT NULL,
    collection_merchant_no varchar2(64) DEFAULT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    currency varchar2(8) DEFAULT 'CNY' NOT NULL,
    notify_url varchar2(1024) DEFAULT NULL,
    success_time timestamp(6) DEFAULT NULL,
    channel_order_no varchar2(64) DEFAULT NULL,
    channel_request_no varchar2(64) DEFAULT NULL,
    channel_error_code varchar2(128) DEFAULT NULL,
    channel_error_msg varchar2(256) DEFAULT NULL,
    channel_notify_data clob,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_fund_order_no ON pay_fund_order (no);

CREATE UNIQUE INDEX uk_pay_fund_order_pay_order ON pay_fund_order (pay_order_id);

CREATE UNIQUE INDEX uk_pay_fund_order_merchant ON pay_fund_order (app_id, merchant_order_id);

CREATE INDEX idx_pay_fund_order_pay_order ON pay_fund_order (pay_order_id);

CREATE INDEX idx_pay_fund_order_status ON pay_fund_order (status, id);

CREATE SEQUENCE pay_fund_detail_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_fund_detail (
    id number(19) NOT NULL,
    no varchar2(64) NOT NULL,
    fund_order_id number(19) NOT NULL,
    fund_order_no varchar2(64) DEFAULT NULL,
    pay_order_id number(19) DEFAULT NULL,
    pay_order_no varchar2(64) DEFAULT NULL,
    trade_order_id number(19) DEFAULT NULL,
    trade_order_sub_id number(19) DEFAULT NULL,
    trade_order_item_id number(19) DEFAULT NULL,
    account_id number(19) DEFAULT NULL,
    account_no varchar2(64) DEFAULT NULL,
    merchant_id number(19) DEFAULT NULL,
    merchant_no varchar2(64) DEFAULT NULL,
    merchant_name varchar2(128) DEFAULT NULL,
    divide_detail_no varchar2(64) DEFAULT NULL,
    biz_type number(10) DEFAULT NULL,
    direction number(10) DEFAULT NULL,
    amount number(19) DEFAULT 0 NOT NULL,
    price number(19) DEFAULT 0 NOT NULL,
    fee_price number(19) DEFAULT 0 NOT NULL,
    divide_amount number(19) DEFAULT NULL,
    refunded_amount number(19) DEFAULT 0 NOT NULL,
    returned_amount number(19) DEFAULT 0 NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    currency varchar2(8) DEFAULT 'CNY' NOT NULL,
    success_time timestamp(6) DEFAULT NULL,
    channel_detail_no varchar2(64) DEFAULT NULL,
    channel_error_code varchar2(128) DEFAULT NULL,
    channel_error_msg varchar2(256) DEFAULT NULL,
    remark varchar2(255) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_fund_detail_no ON pay_fund_detail (no);

CREATE UNIQUE INDEX uk_pay_fund_detail_order_sub_order ON pay_fund_detail (fund_order_id, trade_order_sub_id);

CREATE INDEX idx_pay_fund_detail_order ON pay_fund_detail (fund_order_id);

CREATE INDEX idx_pay_fund_detail_account ON pay_fund_detail (account_no);

CREATE SEQUENCE pay_divide_request_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_divide_request (
    id number(19) NOT NULL,
    no varchar2(64) NOT NULL,
    request_no varchar2(64) DEFAULT NULL,
    app_id number(19) NOT NULL,
    channel_id number(19) DEFAULT NULL,
    channel_code varchar2(32) DEFAULT NULL,
    fund_order_id number(19) NOT NULL,
    fund_order_no varchar2(64) DEFAULT NULL,
    fund_detail_id number(19) DEFAULT NULL,
    fund_detail_no varchar2(64) DEFAULT NULL,
    trade_order_id number(19) DEFAULT NULL,
    trade_order_sub_id number(19) DEFAULT NULL,
    merchant_id number(19) DEFAULT NULL,
    divide_detail_no varchar2(64) DEFAULT NULL,
    merchant_order_id varchar2(64) DEFAULT NULL,
    merchant_request_no varchar2(64) DEFAULT NULL,
    amount number(19) DEFAULT 0 NOT NULL,
    price number(19) DEFAULT 0 NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    retry_count number(10) DEFAULT 0 NOT NULL,
    next_retry_time timestamp(6) DEFAULT NULL,
    channel_request_no varchar2(64) DEFAULT NULL,
    request_data clob,
    response_data clob,
    channel_error_code varchar2(128) DEFAULT NULL,
    channel_error_msg varchar2(256) DEFAULT NULL,
    success_time timestamp(6) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_divide_request_no ON pay_divide_request (no);

CREATE UNIQUE INDEX uk_pay_divide_request_request_no ON pay_divide_request (request_no);

CREATE UNIQUE INDEX uk_pay_divide_request_detail ON pay_divide_request (fund_detail_id);

CREATE UNIQUE INDEX uk_pay_divide_request_channel ON pay_divide_request (channel_request_no);

CREATE UNIQUE INDEX uk_pay_divide_request_merchant ON pay_divide_request (app_id, merchant_request_no);

CREATE UNIQUE INDEX uk_pay_divide_request_fund_order ON pay_divide_request (fund_order_id);

CREATE INDEX idx_pay_divide_request_status ON pay_divide_request (status, id);

CREATE SEQUENCE pay_refund_fund_detail_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_refund_fund_detail (
    id number(19) NOT NULL,
    no varchar2(64) NOT NULL,
    refund_id number(19) DEFAULT NULL,
    refund_no varchar2(64) DEFAULT NULL,
    pay_order_id number(19) DEFAULT NULL,
    pay_order_no varchar2(64) DEFAULT NULL,
    fund_order_id number(19) NOT NULL,
    fund_order_no varchar2(64) DEFAULT NULL,
    fund_detail_id number(19) DEFAULT NULL,
    fund_detail_no varchar2(64) DEFAULT NULL,
    after_sale_id number(19) DEFAULT NULL,
    merchant_id number(19) DEFAULT NULL,
    divide_detail_no varchar2(64) DEFAULT NULL,
    divide_back_request_id varchar2(128) DEFAULT NULL,
    app_id number(19) NOT NULL,
    channel_id number(19) DEFAULT NULL,
    channel_code varchar2(32) DEFAULT NULL,
    merchant_refund_id varchar2(64) DEFAULT NULL,
    amount number(19) DEFAULT 0 NOT NULL,
    price number(19) DEFAULT 0 NOT NULL,
    refund_status number(10) DEFAULT 0 NOT NULL,
    divide_back_status number(10) DEFAULT 10 NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    reason varchar2(255) DEFAULT NULL,
    channel_refund_no varchar2(64) DEFAULT NULL,
    channel_error_code varchar2(128) DEFAULT NULL,
    channel_error_msg varchar2(256) DEFAULT NULL,
    channel_notify_data clob,
    retry_count number(10) DEFAULT 0 NOT NULL,
    next_retry_time timestamp(6) DEFAULT NULL,
    last_error varchar2(512) DEFAULT NULL,
    divide_back_success_time timestamp(6) DEFAULT NULL,
    success_time timestamp(6) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_no ON pay_refund_fund_detail (no);

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_refund_detail ON pay_refund_fund_detail (refund_id, fund_detail_id);

CREATE UNIQUE INDEX uk_pay_refund_fund_detail_refund_after_sale ON pay_refund_fund_detail (refund_id, after_sale_id);

CREATE INDEX idx_pay_refund_fund_detail_refund ON pay_refund_fund_detail (refund_id);

CREATE INDEX idx_pay_refund_fund_detail_order ON pay_refund_fund_detail (fund_order_id);

CREATE SEQUENCE pay_yeepay_merchant_account_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_yeepay_merchant_account (
    id number(19) NOT NULL,
    app_id number(19) NOT NULL,
    merchant_id number(19) DEFAULT NULL,
    merchant_no varchar2(64) DEFAULT NULL,
    request_no varchar2(128) DEFAULT NULL,
    application_no varchar2(128) DEFAULT NULL,
    sign_url varchar2(1024) DEFAULT NULL,
    auth_url varchar2(1024) DEFAULT NULL,
    materials_ref varchar2(255) DEFAULT NULL,
    materials_version number(10) DEFAULT NULL,
    submission_token varchar2(64) DEFAULT NULL,
    submission_expire_time timestamp(6) DEFAULT NULL,
    last_request_time timestamp(6) DEFAULT NULL,
    last_error varchar2(512) DEFAULT NULL,
    merchant_name varchar2(128) DEFAULT NULL,
    account_no varchar2(64) DEFAULT NULL,
    account_name varchar2(128) DEFAULT NULL,
    account_type number(10) DEFAULT NULL,
    onboarding_status number(10) DEFAULT 0 NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    bank_name varchar2(128) DEFAULT NULL,
    bank_account_no varchar2(128) DEFAULT NULL,
    contact_name varchar2(64) DEFAULT NULL,
    contact_phone varchar2(32) DEFAULT NULL,
    channel_data clob,
    remark varchar2(255) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_yeepay_account_merchant ON pay_yeepay_merchant_account (app_id, merchant_id, deleted);

CREATE UNIQUE INDEX uk_pay_yeepay_account_merchant_no ON pay_yeepay_merchant_account (app_id, merchant_no, deleted);

CREATE UNIQUE INDEX uk_pay_yeepay_account_request ON pay_yeepay_merchant_account (app_id, request_no);

CREATE UNIQUE INDEX uk_pay_yeepay_account_application ON pay_yeepay_merchant_account (app_id, application_no);

CREATE INDEX idx_pay_yeepay_account_no ON pay_yeepay_merchant_account (account_no);

CREATE SEQUENCE pay_yeepay_event_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_yeepay_event (
    id number(19) NOT NULL,
    event_id varchar2(128) NOT NULL,
    event_no varchar2(128) DEFAULT NULL,
    event_type varchar2(64) NOT NULL,
    merchant_no varchar2(64) DEFAULT NULL,
    request_id varchar2(128) DEFAULT NULL,
    fund_order_no varchar2(64) DEFAULT NULL,
    payload clob,
    raw_data_ref varchar2(255) DEFAULT NULL,
    encrypted_original clob,
    decrypted_summary clob,
    status number(10) DEFAULT 0 NOT NULL,
    ack_status number(10) DEFAULT 0 NOT NULL,
    retry_count number(10) DEFAULT 0 NOT NULL,
    last_error varchar2(512) DEFAULT NULL,
    event_time timestamp(6) DEFAULT NULL,
    processed_time timestamp(6) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_yeepay_event_id ON pay_yeepay_event (event_id);

CREATE INDEX idx_pay_yeepay_event_status ON pay_yeepay_event (status, id);

CREATE SEQUENCE pay_reconciliation_record_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_reconciliation_record (
    id number(19) NOT NULL,
    batch_no varchar2(64) NOT NULL,
    app_id number(19) NOT NULL,
    channel_id number(19) DEFAULT NULL,
    merchant_no varchar2(64) DEFAULT NULL,
    channel_code varchar2(32) DEFAULT NULL,
    reconciliation_date date NOT NULL,
    total_count number(10) DEFAULT 0 NOT NULL,
    total_amount number(19) DEFAULT 0 NOT NULL,
    total_price number(19) DEFAULT 0 NOT NULL,
    matched_count number(10) DEFAULT 0 NOT NULL,
    matched_amount number(19) DEFAULT 0 NOT NULL,
    difference_amount number(19) DEFAULT 0 NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    file_url varchar2(1024) DEFAULT NULL,
    raw_data clob,
    error_msg varchar2(512) DEFAULT NULL,
    started_time timestamp(6) DEFAULT NULL,
    completed_time timestamp(6) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_reconciliation_batch_no ON pay_reconciliation_record (batch_no);

CREATE INDEX idx_pay_reconciliation_merchant_date ON pay_reconciliation_record (merchant_no, reconciliation_date);

CREATE SEQUENCE pay_fund_operation_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_fund_operation (
    id number(19) NOT NULL,
    action varchar2(64) NOT NULL,
    target_id number(19) NOT NULL,
    operator_id number(19) NOT NULL,
    reason varchar2(512) NOT NULL,
    external_evidence varchar2(1024) NOT NULL,
    previous_state clob,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_fund_operation_target ON pay_fund_operation (tenant_id, action, target_id);

CREATE SEQUENCE pay_reconciliation_difference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pay_reconciliation_difference (
    id number(19) NOT NULL,
    record_id number(19) NOT NULL,
    difference_key varchar2(64) NOT NULL,
    biz_type varchar2(32) NOT NULL,
    request_no varchar2(64) DEFAULT NULL,
    order_no varchar2(64) DEFAULT NULL,
    merchant_no varchar2(64) DEFAULT NULL,
    detail_no varchar2(64) DEFAULT NULL,
    local_amount number(19) DEFAULT NULL,
    channel_amount number(19) DEFAULT NULL,
    difference_amount number(19) DEFAULT NULL,
    reason varchar2(1000) NOT NULL,
    status number(10) DEFAULT 0 NOT NULL,
    operator_id number(19) DEFAULT NULL,
    resolution_reason varchar2(1000) DEFAULT NULL,
    external_evidence varchar2(1000) DEFAULT NULL,
    resolved_time timestamp(6) DEFAULT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_reconciliation_difference ON pay_reconciliation_difference (difference_key);

CREATE INDEX idx_reconciliation_record ON pay_reconciliation_difference (record_id, status);

CREATE SEQUENCE product_merchant_pay_onboarding_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE product_merchant_pay_onboarding (
    id number(19) NOT NULL  PRIMARY KEY,
    merchant_id number(19) NOT NULL,
    merchant_approval_time timestamp(6) NOT NULL,
    app_key varchar2(64) NOT NULL,
    request_no varchar2(64) NOT NULL,
    business_role varchar2(32) NOT NULL,
    state varchar2(32) NOT NULL,
    materials_json clob,
    materials_version number(10) DEFAULT 0 NOT NULL,
    attempt_count number(10) DEFAULT 0 NOT NULL,
    request_accepted number(10) DEFAULT 0 NOT NULL,
    next_retry_time timestamp(6) DEFAULT NULL,
    last_error varchar2(128) DEFAULT NULL,
    onboarding_status number(10) DEFAULT NULL,
    sign_url clob,
    auth_url clob,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL
);

CREATE UNIQUE INDEX uk_merchant_approval ON product_merchant_pay_onboarding (tenant_id, merchant_id, merchant_approval_time);

CREATE INDEX idx_due ON product_merchant_pay_onboarding (state, next_retry_time, tenant_id);

CREATE SEQUENCE product_merchant_pay_material_revision_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE product_merchant_pay_material_revision (
    id number(19) NOT NULL  PRIMARY KEY,
    onboarding_id number(19) NOT NULL,
    merchant_id number(19) NOT NULL,
    materials_version number(10) NOT NULL,
    business_role varchar2(32) NOT NULL,
    operator_id number(19) DEFAULT NULL,
    materials_json clob NOT NULL,
    creator varchar2(64) DEFAULT '',
    create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater varchar2(64) DEFAULT '',
    update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted number(10) DEFAULT 0 NOT NULL,
    tenant_id number(19) DEFAULT 0 NOT NULL
);

CREATE UNIQUE INDEX uk_material_revision ON product_merchant_pay_material_revision (tenant_id, onboarding_id, materials_version);
