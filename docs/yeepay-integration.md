# 易宝多商家支付接入与部署

## 首期资金模型

一个平台交易订单只创建一个 `yeepay_wx_lite` 聚合支付订单，使用平台统一微信 AppID 和固定平台收款商编。平台主商编、收款商编和商家商编均保存在支付时的不可变快照中；历史支付查询、分账、退款和对账使用原快照，不使用后续修改的渠道商编。原 `wx_lite` 渠道继续兼容，历史没有商家快照的普通退款沿用原流程。

交易模块从商家子订单生成分账计划，金额为整数分，分账合计必须等于订单应付金额。支付模块在预支付前校验易宝入网 `COMPLETED`、商家与商编一致、正数金额和最多 5 个商家，再提交资金主单和明细。配置可收紧上限，首期不能放宽到 5 以上。改价同时更新尚未冻结的支付金额与分账计划；已发起渠道支付后禁止改价。

支付成功只触发已支付和履约通知。明确清算成功后，异步发起一次固定金额 `AMOUNT` 分账，商家资金状态与支付状态分别维护。首期仅支持 `DELAY_SETTLE`；`REAL_TIME_DIVIDE` 会在配置及新支付入口被拒绝，避免重复分账。商家暂停或渠道关闭不影响已发生资金业务的查询与补偿。

首期不抽佣。手续费必须由平台费用账户承担，或采用易宝运营确认的 100% 补贴活动。`fullAmountDivideConfirmed` 默认 `false`，未确认保障时直接拒绝新支付。此开关表示实际合同和费用安排已经核实，不提供不存在的易宝实时可分账余额查询，也不能仅凭打开开关视为资金保障已完成。清算查询、分账和对账继续核实真实金额；不足时进入异常处理，绝不减少商家快照金额。

## 官方 SDK 和实际协议

使用易宝官方 `com.yeepay.yop.sdk:yop-java-sdk:4.4.15`、`yop-java-sdk-crypto-inter:4.4.15` 和 `yop-java-sdk-crypto-gm:4.4.15`，由官方 SDK 处理请求签名、响应验签、回调验签和数字信封。支持应用配置要求的 RSA2048 或 SM2/SM3，国密回调需要时使用官方 SM4 实现。默认开启响应验签；跳过验签的开关不允许写入持久化渠道配置，仅供本地协议测试。

依据：[官方 Java SDK 指南](https://open.yeepay.com/docs/open/platform-doc/sdk_guide-sm/java-sdk-guide-sm)、[用户提供的业务接入说明](https://yeepay.feishu.cn/wiki/BzPJwAMWXiUMByksDT1cvvFAnDf)、易宝公开接口定义与官方 SDK 源码。客户端使用以下实际路径：

| 能力 | 官方接口 |
| --- | --- |
| 聚合预支付 | `POST /rest/v1.0/aggpay/pre-pay` |
| 支付、清算查询 | `GET /rest/v1.0/trade/order/query`，清算只读取 `clearStatus` |
| 商户入网 | `POST /rest/v2.0/mer/register/contribute/merchant`；小微主体使用 `contribute/micro` |
| 入网查询 | `GET /rest/v2.0/mer/register/query` |
| 商户状态与通知补发 | `/rest/v1.0/mer/status/query`、`/rest/v2.0/mer/notify/repeat` |
| 分账与查询 | `/rest/v1.0/divide/apply`、`/rest/v1.0/divide/query` |
| 退款与查询 | `/rest/v1.0/trade/refund`、`/rest/v1.0/trade/refund/query` |
| 资金归还与查询 | `/rest/v1.0/divide/back`、`/rest/v1.0/divide/back/query` |
| 资质上传 | YOS `/yos/v1.0/sys/merchant/qual/upload` |

预支付固定 `payWay=MINI_PROGRAM`、`channel=WECHAT`、`scene=ONLINE`，OpenID 由现有登录体系提供。将 `prePayTn` 解码为 `appId/timeStamp/nonceStr/package/signType/paySign`，只将标准微信参数交给小程序 `uni.requestPayment`。小程序不接触易宝商编、密钥或协议字段。API 与文件/账单服务分别使用 YOP、YOS 地址。

SDK 内部写请求重试关闭；注册、预支付、分账、归还、退款的外部幂等号在短事务内提交后才发送网络请求。超时或响应无法验证时先查原单号，不直接创建新请求。只读查询可做有限退避重试；资金任务的间隔、租约与自动重试耗尽单独管理。

## 渠道和外部密钥配置

在支付应用中建立渠道 `yeepay_wx_lite`，首次部署保持渠道禁用。渠道配置 JSON 保存密钥引用，不保存 AppKey、私钥、公钥或证书正文。引用支持 `env://变量名`、`sys://JVM系统属性名`；无前缀形式兼容同名环境变量/系统属性。`env://` 和 `sys://` 显式引用未解析时失败，不把引用字符串当作密钥。无前缀 `privateKeyRef` 保留官方 SDK 凭证别名兼容回退，生产配置建议使用显式引用以避免歧义。

以下只是字段示例；商编、AppID、回调域名必须替换为实际环境值。`@class` 由项目渠道配置序列化使用。

```json
{
  "@class": "cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig",
  "appKeyRef": "env://YEEPAY_APP_KEY",
  "privateKeyRef": "env://YEEPAY_PRIVATE_KEY",
  "publicKeyRef": "env://YEEPAY_PLATFORM_PUBLIC_KEY",
  "serverUrl": "https://openapi.yeepay.com/yop-center",
  "yosServerUrl": "https://yos.yeepay.com/yop-center",
  "sdkProvider": "yeepay",
  "sdkEnv": "prod",
  "signingCertType": "RSA2048",
  "parentMerchantNo": "REPLACE_WITH_PLATFORM_PARENT",
  "collectionMerchantNo": "REPLACE_WITH_PLATFORM_COLLECTOR",
  "appId": "REPLACE_WITH_PLATFORM_MINIPROGRAM_APPID",
  "fundProcessType": "DELAY_SETTLE",
  "businessRole": "SETTLED_MERCHANT",
  "sharePermission": "SHARE",
  "divideBackPermission": "EASYSHARE_BACK_MERCHANT",
  "fullAmountDivideConfirmed": false,
  "feeBearerMode": "PLATFORM_FEE_ACCOUNT",
  "maxDivideRecipients": 5,
  "timeoutSeconds": 10,
  "connectTimeoutSeconds": 5,
  "maxRetries": 3,
  "retryBackoffSeconds": 3,
  "rateLimitPerSecond": 20,
  "paymentNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/payment",
  "clearingNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/clearing",
  "divideNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/divide",
  "refundNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/refund",
  "divideBackNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/divide-back",
  "merchantNotifyUrl": "https://REPLACE_DOMAIN/admin-api/pay/notify/yeepay/merchant"
}
```

公钥和证书二选一。采用证书时移除 `publicKeyRef`，配置 `publicCertificateRef`；SM2 应用按易宝实际要求配置证书或 `platformCertSerialNo`。私钥、公钥格式、证书序列号和测试网关以应用开通资料为准，不混用生产与测试证书。客户端构造回调 URL 时绑定实际 `channelId`；网关须保留查询参数与请求头/正文。

全额补贴模式使用 `feeBearerMode=FULL_SUBSIDY_PROMOTION` 并配置 `feeSubsidyPromotionNo`，客户端按官方 `feeSubsidyInfo` 数组格式提交活动编号。平台费用账户模式不拼造接口字段。[官方补贴契约](https://open.yeepay.com/apis/docs/products/ptssfk/20260923120830/others/5f3cef4420289f001ba82523/656ed87c9a76e60072dd728b.html)及费用账户余额、保障机制需在易宝联调中确认。

已验签回调原文使用独立 AES256-GCM 密钥归档，随机 nonce，租户/渠道/类型/事件绑定作为 AAD；鉴权头及 Cookie 不归档，业务接口不返回原文。

```yaml
yudao:
  pay:
    yeepay:
      callback-archive:
        active-key-id: v1
        keys:
          v1: ${YEEPAY_CALLBACK_ARCHIVE_KEY_BASE64}
```

归档密钥必须为 Base64 编码的 32 字节随机值，通过外部配置注入。轮换时先增加新 key ID，再切换 `active-key-id`，保留旧密钥供受控审计解密历史记录。缺少归档密钥时回调不能完成持久化，也不会提前 ACK。商家材料加密使用既有 MyBatis AES 配置，详见[商户支付入网](yeepay-merchant-onboarding.md)，两类密钥不能混用。

## 退款、回调与人工补偿

售后按原资金明细和商家子订单创建部分退款分配，累计使用 `long` 校验，不因当前商品/商家信息变化重算金额。没有分账计划的历史普通支付不强制要求商家字段。

- 未分账、延迟结算订单：退款内部的 `divideBackDetail` 从原平台收款方归集，不另发 `divide/back`；随后分账只提交扣除已成功退款后的执行金额，原始应得金额快照保持不变。
- 已分账订单：每个商家明细用独立稳定归还号执行 `divide/back`，全部归还成功才调用 `trade/refund`。成功或处理中/结果不确定的归还不能再次提交或释放占用金额。
- 资金已归还而退款失败：退款失败和整体资金人工处理分别记录，已归集金额仍保留，禁止后续售后重复归集。人工操作要求原因、操作者与外部凭证；已提交的终态退款只允许查询原 `refundRequestId` 核实结果，不清空提交时间、不改请求号、不重发终态退款。

六类回调在 `/admin-api/pay/notify/yeepay/{payment,clearing,divide,refund,divide-back,merchant}`，均使用可信 `channelId` 找到渠道和租户。官方 SDK 验签解密后校验原订单、商编和金额，同事务保存事件及加密原文，以可信渠道加外部事件键去重，然后返回官方大写 `SUCCESS` 与必要响应头。业务处理走独立线程池；线程池饱和仍保留可重放事件。数据库持久化失败、验签失败或资金身份不符不能返回成功。

后台 `apps/web-antd` 的 `/pay/fund` 展示支付、清算、分账及退款/归还独立状态、对账差异和操作记录。分账重试、退款核查、对账重跑/关闭差异分别授权；操作前刷新实际状态，禁止迟到结果覆盖新对象。权限种子不自动授予角色。

## 数据库安装与升级

提供 MySQL、PostgreSQL、Oracle、SQL Server、达梦、人大金仓、海量数据库和 openGauss 八种脚本。资金模块九张表包含主单、商家明细、分账请求、退款分配、账户、回调事件、对账批次/差异、人工操作记录；商品域另有入网队列和补料版本表。

1. 备份并确认原支付和商家表已经存在，使用与数据库匹配的 `sql/<vendor>/`。首次执行 `yeepay_fund.sql`，再执行 `yeepay_existing_payment_upgrade.sql` 补齐原支付单快照、退款分配和退款提交时间。非 MySQL 建表脚本执行一次，已存在表时不要重复执行。
2. MySQL 再执行 `yeepay_merchant_onboarding.sql` 建任务表并回填历史审核通过商家；其他数据库在 11 张表安装后执行 `yeepay_merchant_onboarding_backfill.sql`。
3. 执行商家敏感字段扩容脚本，保留旧 AES 密钥。只有确认旧数据为纯明文的部署才执行显式明文标记迁移，停止商家写入，按入网文档启用回填后关闭迁移开关。
4. MySQL 执行 `yeepay_admin_menu.sql`，分配权限。其他数据库通过菜单管理配置对应组件/权限。

MySQL 支付升级脚本也包含早期试验资金表的必要补列；其他数据库升级脚本针对原支付基础表，资金表必须使用本次完整首次安装结构。已经自行部署早期易宝资金表的环境须逐列对比并审查专项 ALTER，不能把 CREATE 脚本当作已有资金表升级。`pay_refund_yeepay_submission.sql` 与 `yeepay_merchant_submission.sql` 是 MySQL 独立补列兼容脚本，完整安装后不必重复执行。

`db/yudao.sql` 及 MySQL 全量初始化脚本已同步本次结构。Oracle/达梦 CLOB 转换需要停写且只执行一次，保留原列可空性。SQL Server 对可空商编等采用过滤唯一索引，避免多个待审商户因 NULL 冲突。支持脚本不代表已在所有厂商的生产数据库执行；最终验证范围见[审查记录](yeepay-review.md)。

## 调度、对账和灰度

启动前在 XXL-JOB 配置以下租户任务，并保留现有支付、退款、业务通知和过期任务：

| Handler | 建议安排 | 职责 |
| --- | --- | --- |
| `payFundDivideRetryJob` | 每 10～30 秒，依据吞吐调节 | 清算查询、分账补偿、失败事件重放；每单请求间隔由资金状态机限制 |
| `payYeepayReconciliationJob` | 北京时间每日 09:10 后 | 前一日支付/分账账单与退款、归还资金记录双向对账 |
| `payOrderSyncJob` / `payRefundSyncJob` | 保留现有安排 | 支付与退款处理中原单查询 |
| `payNotifyJob` / `payOrderExpireJob` | 保留现有安排 | 业务回调补偿与超时关闭 |

商品域入网调度默认每 30 秒执行，支持先关闭再补资料。账单依据[官方商户对账说明](https://open.yeepay.com/docs/products/ptssfk/others/5f9a6ea1f8301e001b1a870f)，通过 YOS 获取次日账单，原平台作用域查询包含已经禁用的历史渠道。严格核对 UTF-8、日期、表头、汇总、商编、订单号、整数分金额、状态和明细号。外部孤儿、内部成功流水缺失、金额/状态不符均建立差异；重跑不清除尚未解决差异，关闭差异必须记录证据。

上线顺序：安装表与配置，保持新渠道及费用保障关闭；测试环境完成入网、预支付、回调、清算、分账、两种部分退款、日账单和人工补偿；核实易宝收款角色/权限与手续费安排后，仅对少量完成入网商家开通。监控支付成功率、清算延迟、分账成功率、退款/归还成功率、最老待处理任务及对账差异金额。

回退时禁用新支付渠道，保留历史查询、归还、退款、对账和事件补偿任务，不删除资金表或改写快照。密钥轮换保留历史验证能力；部署更换服务前先检查在途请求和队列。实际收款角色、最大接收方数、手续费保障、商家权限、回调状态语义或退款顺序未完成真实易宝联调时，不得开启生产放量。

## 验收边界

代码验证包括官方 SDK 本地 HTTP 契约、Mockito 状态边界、H2 事务/并发、商家加密与授权、管理前端及小程序构建。真实商户开户、生产证书、易宝权限/费用账户和实际资金流并未由本地自动化替代。联调所需配置和外部能力确认是部署条件；详见[审查记录](yeepay-review.md)中的需求映射和实际验证结果。
