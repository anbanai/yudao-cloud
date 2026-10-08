-- 会员积分倍率与权益中心增量迁移
-- 执行前请确认已备份数据库；脚本可重复执行（字段和表存在时跳过）。

SET @member_level_multiplier_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_level'
      AND COLUMN_NAME = 'point_trade_give_multiplier'
);
SET @member_level_multiplier_sql := IF(@member_level_multiplier_exists = 0,
    'ALTER TABLE member_level ADD COLUMN point_trade_give_multiplier int NOT NULL DEFAULT 1000 COMMENT ''消费返积分倍率（千分比）'' AFTER discount_percent',
    'SELECT 1');
PREPARE member_level_multiplier_stmt FROM @member_level_multiplier_sql;
EXECUTE member_level_multiplier_stmt;
DEALLOCATE PREPARE member_level_multiplier_stmt;

SET @member_level_seed_sql := IF(@member_level_multiplier_exists = 0,
    'UPDATE member_level SET point_trade_give_multiplier = CASE level WHEN 1 THEN 1000 WHEN 2 THEN 1200 WHEN 3 THEN 1500 WHEN 4 THEN 2000 WHEN 5 THEN 2000 END WHERE level BETWEEN 1 AND 5',
    'SELECT 1');
PREPARE member_level_seed_stmt FROM @member_level_seed_sql;
EXECUTE member_level_seed_stmt;
DEALLOCATE PREPARE member_level_seed_stmt;

-- Existing installations may have their own experience thresholds. Preserve them;
-- only add missing levels with ascending defaults that operators can adjust.
INSERT INTO member_level (name, level, experience, discount_percent, point_trade_give_multiplier,
                          icon, background_url, status, tenant_id)
SELECT seed.name, seed.level, seed.experience, seed.discount_percent, seed.multiplier, '', '', 0, tenant.id
FROM (
  SELECT '青苗' name, 1 level, 0 experience, 100 discount_percent, 1000 multiplier
  UNION ALL SELECT '新竹', 2, 100, 98, 1200
  UNION ALL SELECT '嘉木', 3, 1000, 95, 1500
  UNION ALL SELECT '金榕', 4, 5000, 90, 2000
  UNION ALL SELECT '玄圭', 5, 10000, 88, 2000
) seed CROSS JOIN system_tenant tenant
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM member_level l WHERE l.tenant_id = tenant.id AND l.level = seed.level);

UPDATE member_level SET point_trade_give_multiplier = CASE level
    WHEN 1 THEN 1000 WHEN 2 THEN 1200 WHEN 3 THEN 1500 WHEN 4 THEN 2000 WHEN 5 THEN 2000 END
WHERE level BETWEEN 1 AND 5 AND @member_level_multiplier_exists = 0;

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component,
                         component_name, status, visible, keep_alive, always_show)
SELECT '会员权益', '', 2, 3, id, 'benefit', 'lucide:gift', 'member/benefit/index',
       'MemberBenefit', 0, b'1', b'1', b'0'
FROM system_menu parent WHERE parent.parent_id = 0 AND parent.path = '/member'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.parent_id = parent.id AND existing.path = 'benefit');

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show)
SELECT permissions.name, permissions.permission, 3, permissions.sort, menu.id, '', '', '',
       0, b'1', b'0', b'0'
FROM system_menu menu JOIN (
  SELECT '权益查询' name, 'member:benefit:query' permission, 1 sort
  UNION ALL SELECT '权益创建', 'member:benefit:create', 2
  UNION ALL SELECT '权益修改', 'member:benefit:update', 3
  UNION ALL SELECT '权益删除', 'member:benefit:delete', 4
  UNION ALL SELECT '权益额度操作', 'member:benefit:operate', 5
) permissions
WHERE menu.path = 'benefit' AND menu.parent_id = (SELECT id FROM system_menu WHERE parent_id = 0 AND path = '/member' LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.parent_id = menu.id
      AND existing.permission = permissions.permission);

-- Order and point snapshots are nullable so historical records retain their original meaning.
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member_point_record' AND COLUMN_NAME = 'point_calculate_price') = 0,
    'ALTER TABLE member_point_record ADD COLUMN point_calculate_price int NULL COMMENT ''积分计算基数，单位：分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member_point_record' AND COLUMN_NAME = 'point_give_base') = 0,
    'ALTER TABLE member_point_record ADD COLUMN point_give_base int NULL COMMENT ''基础赠送积分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member_point_record' AND COLUMN_NAME = 'point_give_multiplier') = 0,
    'ALTER TABLE member_point_record ADD COLUMN point_give_multiplier int NULL COMMENT ''会员积分倍率，千分比''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;

SET @coupon_source_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'promotion_coupon' AND COLUMN_NAME = 'source_biz_id') = 0,
    'ALTER TABLE promotion_coupon ADD COLUMN source_biz_id varchar(64) NULL COMMENT ''权益发券业务单号''', 'SELECT 1');
PREPARE member_coupon_stmt FROM @coupon_source_sql; EXECUTE member_coupon_stmt; DEALLOCATE PREPARE member_coupon_stmt;
SET @coupon_index_sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'promotion_coupon' AND INDEX_NAME = 'uk_coupon_source_biz_id') = 0,
    'SELECT 1', 'ALTER TABLE promotion_coupon DROP INDEX uk_coupon_source_biz_id');
PREPARE member_coupon_index_stmt FROM @coupon_index_sql; EXECUTE member_coupon_index_stmt; DEALLOCATE PREPARE member_coupon_index_stmt;

SET @coupon_tenant_index_sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'promotion_coupon' AND INDEX_NAME = 'uk_coupon_source_biz_tenant') = 0,
    'ALTER TABLE promotion_coupon ADD UNIQUE KEY uk_coupon_source_biz_tenant (source_biz_id, tenant_id)', 'SELECT 1');
PREPARE member_coupon_tenant_index_stmt FROM @coupon_tenant_index_sql; EXECUTE member_coupon_tenant_index_stmt; DEALLOCATE PREPARE member_coupon_tenant_index_stmt;

SET @birthday_advance_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member_level_benefit' AND COLUMN_NAME = 'birthday_advance_days') = 0,
    'ALTER TABLE member_level_benefit ADD COLUMN birthday_advance_days int NOT NULL DEFAULT 0 COMMENT ''生日权益提前发放天数'' AFTER validity_days', 'SELECT 1');
PREPARE member_birthday_advance_stmt FROM @birthday_advance_sql; EXECUTE member_birthday_advance_stmt; DEALLOCATE PREPARE member_birthday_advance_stmt;

SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'point_give_calculate_price') = 0,
    'ALTER TABLE trade_order ADD COLUMN point_give_calculate_price int NULL COMMENT ''积分计算基数，单位：分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'point_give_multiplier') = 0,
    'ALTER TABLE trade_order ADD COLUMN point_give_multiplier int NULL COMMENT ''会员积分倍率，千分比''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'point_give_base') = 0,
    'ALTER TABLE trade_order ADD COLUMN point_give_base int NULL COMMENT ''基础赠送积分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;

SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order_item' AND COLUMN_NAME = 'point_give_calculate_price') = 0,
    'ALTER TABLE trade_order_item ADD COLUMN point_give_calculate_price int NULL COMMENT ''积分计算基数，单位：分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order_item' AND COLUMN_NAME = 'point_give_multiplier') = 0,
    'ALTER TABLE trade_order_item ADD COLUMN point_give_multiplier int NULL COMMENT ''会员积分倍率，千分比''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;
SET @snapshot_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_order_item' AND COLUMN_NAME = 'point_give_base') = 0,
    'ALTER TABLE trade_order_item ADD COLUMN point_give_base int NULL COMMENT ''基础赠送积分''', 'SELECT 1');
PREPARE member_snapshot_stmt FROM @snapshot_sql; EXECUTE member_snapshot_stmt; DEALLOCATE PREPARE member_snapshot_stmt;

CREATE TABLE IF NOT EXISTS member_benefit_definition (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    code varchar(64) NOT NULL COMMENT '权益编码',
    name varchar(64) NOT NULL COMMENT '权益名称',
    type tinyint NOT NULL DEFAULT 1 COMMENT '权益类型',
    description varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
    icon varchar(255) NOT NULL DEFAULT '' COMMENT '图标',
    action_type tinyint NOT NULL DEFAULT 0 COMMENT '动作类型，0展示，1领取，2申请',
    coupon_template_id bigint DEFAULT NULL COMMENT '优惠券模板编号',
    status tinyint NOT NULL DEFAULT 0 COMMENT '状态',
    sort int NOT NULL DEFAULT 0 COMMENT '排序',
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_code_tenant (code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员权益定义';

CREATE TABLE IF NOT EXISTS member_level_benefit (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    level_id bigint NOT NULL COMMENT '会员等级', benefit_id bigint NOT NULL COMMENT '权益定义',
    quantity int NOT NULL DEFAULT 1 COMMENT '额度', period_type tinyint NOT NULL DEFAULT 1 COMMENT '周期：1一次性，2季度，3年度，4生日',
    claim_type tinyint NOT NULL DEFAULT 0 COMMENT '领取方式：0自动到账，1用户领取', validity_days int NOT NULL DEFAULT 365,
    birthday_advance_days int NOT NULL DEFAULT 0 COMMENT '生日权益提前发放天数',
    status tinyint NOT NULL DEFAULT 0, sort int NOT NULL DEFAULT 0,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_level_benefit_tenant (level_id, benefit_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级权益绑定';

CREATE TABLE IF NOT EXISTS member_user_benefit (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '编号', user_id bigint NOT NULL, level_id bigint NOT NULL, benefit_id bigint NOT NULL,
    period_start datetime NOT NULL, period_end datetime NOT NULL, granted_quantity int NOT NULL DEFAULT 0,
    available_quantity int NOT NULL DEFAULT 0, used_quantity int NOT NULL DEFAULT 0,
    claimed_quantity int NOT NULL DEFAULT 0,
    claim_type tinyint NOT NULL DEFAULT 0, claim_status tinyint NOT NULL DEFAULT 0, status tinyint NOT NULL DEFAULT 0,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_user_benefit_period (user_id, level_id, benefit_id, period_start, tenant_id),
    KEY idx_user_status (user_id, status, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权益额度账户';

SET @claimed_quantity_sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member_user_benefit' AND COLUMN_NAME = 'claimed_quantity') = 0,
    'ALTER TABLE member_user_benefit ADD COLUMN claimed_quantity int NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE member_claimed_stmt FROM @claimed_quantity_sql;
EXECUTE member_claimed_stmt;
DEALLOCATE PREPARE member_claimed_stmt;

CREATE TABLE IF NOT EXISTS member_benefit_ledger (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '编号', user_id bigint NOT NULL, account_id bigint NOT NULL, benefit_id bigint NOT NULL,
    event_type tinyint NOT NULL COMMENT '事件：1发放，2领取，3使用，4过期，5撤销，6补发，7调整',
    quantity int NOT NULL DEFAULT 0, biz_id varchar(64) NOT NULL DEFAULT '', reason varchar(255) NOT NULL DEFAULT '', operator_id bigint DEFAULT NULL,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), KEY idx_user_benefit_time (user_id, benefit_id, create_time), UNIQUE KEY uk_biz_event_tenant (biz_id, event_type, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权益流水';

INSERT IGNORE INTO member_benefit_definition (code, name, type, description, action_type, sort, tenant_id)
SELECT seed.code, seed.name, seed.type, seed.description, seed.action_type, seed.sort, tenant.id
FROM (
 SELECT 'NEWCOMER_COUPON' code, '新人立减券（待配置券模板）' name, 3 type, 'V1 注册新人礼券，待运营配置优惠券模板后可领取' description, 1 action_type, 10 sort
 UNION ALL SELECT 'LEVEL_GIFT', '等级礼包券', 3, '等级礼券，具体门槛和面额以券模板为准', 1, 20
 UNION ALL SELECT 'MEMBER_DISCOUNT', '会员折扣', 1, '结算时按会员等级自动享受折扣', 0, 30
 UNION ALL SELECT 'BIRTHDAY_COUPON', '生日礼券（待配置券模板）', 3, '生日窗口生成，待运营配置优惠券模板后可领取', 1, 40
 UNION ALL SELECT 'POINT_MULTIPLIER', '购物返积分倍率', 2, '确认收货后按会员等级倍率发放积分', 0, 50
 UNION ALL SELECT 'TEA_TRIAL', '好物试用', 4, '年度试用申请额度，待服务接入', 2, 60
 UNION ALL SELECT 'EXCLUSIVE_CONTENT', '专属内容服务', 4, '季度茶生活内容服务，待服务接入', 2, 70
 UNION ALL SELECT 'COMMUNITY', '会员社群', 4, '会员社群邀请，待服务接入', 2, 80
 UNION ALL SELECT 'CUSTOM_TEA', 'VIP 专属定制', 4, '年度定制申请额度，待服务接入', 2, 90
 UNION ALL SELECT 'QUARTERLY_COUPON', '季度券包', 3, '每自然季度生成领取额度', 1, 100
 UNION ALL SELECT 'BIRTHDAY_GIFT', '生日定制礼', 4, '生日礼品申请，待服务接入', 2, 110
 UNION ALL SELECT 'PRIORITY_SHOPPING', '购物优先权', 4, '新品优先购买，待服务接入', 2, 120
 UNION ALL SELECT 'ORDER_CASHBACK', '购物返利', 4, '订单返利，待结算服务接入', 2, 130
 UNION ALL SELECT 'PRIVATE_ASSISTANT', '私人饮茶助理', 4, '专属茶顾问服务，待服务接入', 2, 140
 UNION ALL SELECT 'ANNUAL_QUOTA', '年度礼遇', 4, '会员年度礼遇额度，待服务接入', 2, 150
 UNION ALL SELECT 'EVENT_INVITATION', '活动优选邀约', 4, '会员活动邀约，待活动服务接入', 2, 160
) seed CROSS JOIN system_tenant tenant
WHERE tenant.deleted = b'0';

INSERT IGNORE INTO member_level_benefit (level_id, benefit_id, quantity, period_type, claim_type, validity_days, birthday_advance_days, sort, tenant_id)
SELECT l.id, d.id,
       CASE d.code
         WHEN 'TEA_TRIAL' THEN GREATEST(l.level - 1, 1)
         WHEN 'EXCLUSIVE_CONTENT' THEN GREATEST(LEAST(l.level - 1, 3), 1)
         WHEN 'CUSTOM_TEA' THEN GREATEST(l.level - 2, 1)
         WHEN 'QUARTERLY_COUPON' THEN GREATEST(l.level - 2, 1)
         ELSE 1 END,
       CASE d.code
         WHEN 'QUARTERLY_COUPON' THEN 2 WHEN 'EXCLUSIVE_CONTENT' THEN 2
         WHEN 'ANNUAL_QUOTA' THEN 3 WHEN 'TEA_TRIAL' THEN 3
         WHEN 'CUSTOM_TEA' THEN 3 WHEN 'BIRTHDAY_COUPON' THEN 4
         WHEN 'BIRTHDAY_GIFT' THEN 4 ELSE 1 END,
       CASE WHEN d.type = 3 THEN 1 ELSE 0 END, 365,
       CASE WHEN d.code IN ('BIRTHDAY_COUPON', 'BIRTHDAY_GIFT') THEN 7 ELSE 0 END,
       d.sort, l.tenant_id
FROM member_level l JOIN member_benefit_definition d ON d.tenant_id = l.tenant_id
WHERE l.level BETWEEN 1 AND 5
  AND (d.code = 'POINT_MULTIPLIER'
    OR (l.level = 1 AND d.code = 'NEWCOMER_COUPON')
    OR (l.level >= 2 AND d.code IN ('LEVEL_GIFT', 'MEMBER_DISCOUNT', 'BIRTHDAY_COUPON',
        'TEA_TRIAL', 'EXCLUSIVE_CONTENT', 'COMMUNITY'))
    OR (l.level >= 3 AND d.code IN ('CUSTOM_TEA', 'QUARTERLY_COUPON', 'BIRTHDAY_GIFT',
        'ORDER_CASHBACK', 'EVENT_INVITATION'))
    OR (l.level >= 4 AND d.code IN ('PRIORITY_SHOPPING', 'PRIVATE_ASSISTANT', 'ANNUAL_QUOTA')));

CREATE TABLE IF NOT EXISTS member_point_batch (
    id bigint NOT NULL AUTO_INCREMENT, user_id bigint NOT NULL, source_record_id bigint DEFAULT NULL,
    original_point int NOT NULL DEFAULT 0, remaining_point int NOT NULL DEFAULT 0,
    earned_time datetime NOT NULL, expire_time datetime DEFAULT NULL, legacy tinyint NOT NULL DEFAULT 0,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0有效，1已过期',
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_source_record_tenant (source_record_id, tenant_id),
    KEY idx_user_available (user_id, status, expire_time), KEY idx_source_record (source_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分有效期批次';

CREATE TABLE IF NOT EXISTS member_point_consume (
    id bigint NOT NULL AUTO_INCREMENT, user_id bigint NOT NULL, batch_id bigint NOT NULL, record_id bigint DEFAULT NULL,
    quantity int NOT NULL DEFAULT 0, biz_id varchar(64) NOT NULL, restored int NOT NULL DEFAULT 0,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_user_biz_batch (user_id, biz_id, batch_id), KEY idx_batch (batch_id), KEY idx_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分批次消费明细';

-- Existing balances remain usable during rollout. These legacy batches never expire.
INSERT INTO member_point_batch (user_id, original_point, remaining_point, earned_time, legacy, status, tenant_id)
SELECT u.id, u.point, u.point, COALESCE(u.create_time, CURRENT_TIMESTAMP), 1, 0, u.tenant_id
FROM member_user u LEFT JOIN member_point_batch b ON b.user_id = u.id AND b.legacy = 1
WHERE COALESCE(u.point, 0) > 0 AND b.id IS NULL;
