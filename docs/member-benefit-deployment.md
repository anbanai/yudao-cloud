# 会员权益数据库升级

会员信息接口会读取 `member_level.point_trade_give_multiplier`。生产库必须先完成这项增量迁移，再发布包含会员权益代码的后端；否则 `/app-api/member/user/get` 会因缺少列返回 500，商城“我的”页面无法加载。

## 发布顺序

1. 备份目标 MySQL 数据库。
2. 在与应用相同的数据库上执行 [`sql/mysql/member_benefit.sql`](../sql/mysql/member_benefit.sql)。脚本按字段、表和索引存在性判断，支持重复执行。
   如果旧版本脚本曾在 `member_level_benefit` 报“表不存在”处中断，直接使用当前版本重新执行；前置 DDL 已完成的部分会跳过，缺失的会员权益表会继续创建。
3. 发布后端并检查启动日志。
4. 用实际租户登录，确认 `/app-api/member/user/get` 返回 200；再检查会员等级、积分和权益接口。

执行后可以用下面的查询做最小预检：

```sql
SELECT COUNT(*) AS column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member_level'
  AND COLUMN_NAME = 'point_trade_give_multiplier';
```

`column_exists` 必须为 `1`。同时确认 1 至 5 级的倍率已经填充，避免只加了字段但没有完成数据初始化：

```sql
SELECT level, point_trade_give_multiplier
FROM member_level
WHERE level BETWEEN 1 AND 5
ORDER BY level;
```

如果仍为 `0`，或倍率不完整，不要启动新版后端，先检查脚本执行的数据库连接、权限和后续 DDL 错误。迁移脚本可以重跑，会补齐缺失的默认倍率和未完成的结构变更；它不会覆盖已经设置成非默认值的倍率。应用端保留接口错误并显示重试入口，不会用空的会员数据掩盖迁移失败。

同时确认会员权益核心表已经全部存在：

```sql
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('member_benefit_definition', 'member_level_benefit',
                     'member_user_benefit', 'member_benefit_ledger')
ORDER BY TABLE_NAME;
```

结果必须包含上述 4 张表；缺少任意一张都说明迁移没有完成，应先重跑脚本并检查执行器返回的第一条错误。
