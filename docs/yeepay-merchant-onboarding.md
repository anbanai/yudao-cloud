# 商户支付入网

商品域以现有 `product_merchant` 的审核通过状态为入口。审批事务更新商户状态后，只建立持久化支付入网任务；定时任务随后提交易宝资料、查询审核结果。商品域通过 `PayFundApi` 调用支付域，渠道密钥仅由支付域持有。

## 审核快照与重试

任务按租户、商户编号、审核时间去重。审核时间优先使用原有 `review_time`，历史记录缺失时使用 `update_time`。同一次审核重复建立任务返回原任务编号；新审核快照使旧任务进入 `SUPERSEDED`。冻结或暂时不处于审核通过状态的商户暂缓处理。

任务的主要状态为 `WAITING_MATERIALS`（待补料）、`READY`（待提交）、`RETRY`（重试）、`SUBMITTED`（审核中）、`ACTION_REQUIRED`（签约、认证或补料）、`COMPLETED`（完成）、`SUPERSEDED`（快照已更新）。请求号固定为 `MER-{tenantId}-{merchantId}`，补料版本单独递增；渠道已受理的请求先查结果，避免查询失败后重新注册。

支付域在短事务中提交稳定请求号和提交租约，随后在数据库事务外调用 SDK。有效租约期间并发注册或查询返回处理中；租约到期后的旧响应不能覆盖新提交结果，已完成的状态不能被迟到响应降级。

## 部署顺序

1. 确认原有商户、资质、支付基础表已建立，选择 `sql/<数据库>/` 中与部署数据库匹配的脚本。当前提供 MySQL、PostgreSQL、Oracle、SQL Server、达梦、人大金仓、海量数据库、openGauss 八种版本。
2. 按资金集成文档完成支付升级和资金建表。非 MySQL 的 `yeepay_fund.sql` 包含 11 张资金与商户任务表；MySQL 的资金脚本包含 9 张支付表，商户任务表在第 4 步建立。已有支付部署先执行对应版本的 `yeepay_existing_payment_upgrade.sql`。MySQL 的 `yeepay_merchant_submission.sql` 可单独补齐已存在商户账户表的提交租约字段，并将尚未分配的商户号统一为 `NULL`。
3. 执行对应数据库的 `yeepay_merchant_encryption_columns.sql` 扩大原有敏感字段列宽，保留现有 AES 密文和原密钥。仅对确认来自旧版本纯明文商户表的部署，再执行对应的 `yeepay_merchant_encryption_migration.sql` 标记来源。Oracle、达梦的资质文件地址转换为 CLOB 时，必须停止商户写入；转换段只执行一次，并保持原列的可空性约束。
4. MySQL 执行 `yeepay_merchant_onboarding.sql` 建立任务、补料审计表并回填历史已审核通过商户；其他七种数据库在资金建表后执行 `yeepay_merchant_onboarding_backfill.sql` 回填队列。默认支付应用为 `mall`；使用其他应用标识时，先修改回填 SELECT 中的应用标识，并与商品域配置保持一致。
5. 执行 `sql/mysql/yeepay_admin_menu.sql`，在角色管理中分配所需菜单及权限，再刷新权限缓存或重新登录。
6. 配置私有主文件存储、加密密钥、支付应用及易宝渠道后启动服务。可以先关闭自动调度，检查待补料任务，再开启调度。

上述八种数据库的资金表、商户任务、敏感字段扩容、明文迁移与历史队列脚本均不依赖商家申请表、商家账号归属新字段或新版入驻流程。菜单种子目前使用 MySQL 语法；其他数据库可在后台菜单管理中按相同组件及权限建立菜单。原生数据库执行与易宝沙箱联调情况以资金集成部署说明中的验证记录为准。

## 加密迁移

新敏感字段写入使用 `AES:v1:` 标记。原有合法无前缀 AES 密文保持可读；损坏密文、密钥不匹配或未标记的原始明文会报固定错误，不会因解密失败回退为明文。已有加密部署必须保留原密钥；通过环境配置 `mybatis-plus.encryptor.password` 提供合法长度的 AES 密钥，不将密钥写入脚本或提交到仓库。

纯明文迁移脚本只处理原有 `product_merchant` 的信用代码、法人、联系人、手机，以及 `product_merchant_qualification` 的证件编号和文件地址。脚本在迁移账本中记录来源并明确加 `PLAIN:v0:` 前缀；重复执行不会重复标记。执行标记前停止商户写入，迁移期间暂时启用：

MySQL 迁移脚本不创建存储过程，也不使用 `DELIMITER`。这是为了兼容不支持复合语句的 MySQL 兼容执行器；请先停止商户写入并确保回填任务未运行，再按脚本顺序逐条执行每个 SQL 语句，不要把整个文件作为一个请求发送。脚本把迁移账本写入放在最后，中断后可以从未完成的语句继续执行。已存在无前缀 AES 密文或 `AES:v1:` 密文的部署必须跳过纯明文标记脚本。

```yaml
yudao:
  product:
    merchant:
      encryption:
        allow-marked-plaintext: true
        backfill-enabled: true
```

启动回填按字段、按批次提交，覆盖各租户和软删除记录，中断后可以继续。确认 `product_merchant_encryption_migration` 中 `merchant-plaintext-v0-to-aes-v1` 的状态为 `ENCRYPTED`，且原有敏感字段已无 `PLAIN:v0:` 后，关闭两个开关并重新启动。已存在 AES 密文的部署不能运行纯明文标记脚本。

任务资料 JSON、补料版本审计、签约和认证 URL 在商品域加密保存。管理页面不回显已保存的原始资料；关闭或切换详情时清空编辑输入。资料和私有上传接口关闭请求与响应访问日志，校验失败返回固定提示。

## 商品域配置

```yaml
yudao:
  product:
    merchant-pay:
      app-key: mall
      retry-seconds: 60
      poll-seconds: 120
      scheduler-enabled: true
      scheduler-delay-millis: 30000
```

这些值有默认值，现有生产 YAML 无须新增商家申请配置。商品域需要私有主文件存储支持。上传接口只接受 JPEG、PNG、PDF，单文件不超过 10 MB，随机文件名存入 `merchant-pay/{tenantId}/{onboardingId}`。提交资料时校验文件编号属于当前上传账号；后台处理只为已授权文件生成 15 分钟读取链接，资料和审计中保存文件编号引用。

## 管理页面与接口

前端限定 `apps/web-antd`，页面路径 `/pay/fund/merchant`，组件 `pay/fund/merchant`。查询权限 `pay:fund:merchant:query`，建立任务、上传、补料权限 `pay:fund:merchant:update`。

接口前缀 `/product/merchant/pay-onboarding`：

| 方法与路径 | 请求 | 结果 |
| --- | --- | --- |
| `POST /enqueue` | 查询参数 `merchantId` | 创建或返回审核快照的任务编号 |
| `POST /private-file` | multipart `onboardingId`、`file` | 私有文件编号 |
| `POST /materials` | JSON `onboardingId`、`businessRole`、`materials`、`fileReferences` | 保存资料及加密审计版本 |
| `GET /get` | 查询参数 `onboardingId` | 本租户任务状态、缺料清单、HTTPS 签约/认证链接 |
| `GET /page` | 分页、可选 `merchantId`、`onboardingId`、`state` | 本租户分页任务，不包含资料或签约链接 |

`businessRole` 支持 `SHARE_MERCHANT` 和 `SETTLED_MERCHANT`。`materials` 只允许官方资料组：`merchantType`、`merchantSubjectInfo`、`merchantCorporationInfo`、`merchantContactInfo`、`businessAddressInfo`、`functionServiceQualificationInfo`、`accountInfo`、`industryCategoryInfo`、`productInfo`、`functionService`。主体、法人/经营者、经营地址和 SHARE 场景必须完整；小微类型联系人组可选。具体组内字段随主体类型、业务角色及易宝签约要求变化，按官方文档准备，页面目前使用 JSON 输入，没有替代官方完整资料清单。

`fileReferences` 示例为 `{"merchantSubjectInfo.businessLicenseUrl": 71}`，键只接受允许组下的一层字段，值为当前账号上传的正整数文件编号。请求号、渠道密钥和私钥不得通过资料对象或文件引用注入。签约/认证链接只接受没有嵌入账号密码的 HTTPS 地址。

当前验证覆盖加密读写与迁移、审核快照队列、跨租户访问、私有文件授权、补料版本、失败恢复、并发租约及迟到响应。易宝合同、平台手续费承担方式、完整资料要求和真实沙箱联调仍需实际接入验证；本页不将未完成的外部验证表示为已通过。
