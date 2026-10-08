# 商品分组运营说明

商品分组是附加的运营维度。商品仍须选择一个主分类；同一商品可以同时属于多个平级分组。分类导航、原分类查询、优惠适用范围和结算仍使用原来的分类或商品规则。

## 管理后台

- 商品中心 → 商品分组：维护名称、排序、启用状态和「商城公开」设置。历史分组默认公开；内部分组只供后台运营使用。
- 点击分组的商品数或在售商品数进入成员页，可打开商品详情、编辑商品、添加已有商品、移出成员及调整组内排序。「添加商品」不创建商品，也不改变上下架状态。
- 商品列表可同时按主分类、名称、日期、商品状态和分组筛选；选择多个分组时匹配其中任一分组并去重。「未分组」包括没有任何有效分组关系的商品，加入内部分组的商品不属于未分组。
- 商品列表支持跨页勾选，批量加入或移出所选分组。每次最多 200 件商品、15 个分组；其他分组关系保留。停用分组不能新增成员，可以移出已有成员。
- 商品新增/编辑保留必填的单一「商品分类」，另设多选「商品分组」。停用分组的已有关系只读展示，可通过分组批量移出功能调整。
- 通用选品器支持分组筛选，覆盖装修手选商品及复用该选品器的营销入口。活动提交的仍是明确的商品/SKU ID 列表；以后修改分组成员不会自动改变已保存的活动范围。

## 装修与商城

- 「商品分类」和「商品分组」两个组件同时保留；分组组件按分组实时查询商品。
- 「商品栏」支持手动选择或按分组取商品。切换来源保留各自配置；旧配置未指定来源时仍按手动商品清单展示。
- 按分组选品最多 15 组，首次展示最多 50 件商品。单组默认按组内排序，多组去重后按商品全局排序；可选择销量、新品、价格排序。
- 导航、图片等使用统一链接选择器时，可选择「商品分组列表」或「商品分组浏览」页面。分组页支持搜索、排序、重新筛选分组及分享当前筛选。
- 分组页支持好友、朋友圈和链接分享；筛选条件不适合现有海报的小程序码参数长度限制，因此这两个页面不提供海报入口。其他页面海报功能保留。
- 商城分组入口和商品详情分组标签只显示启用且公开的分组。分组改为内部或停用后，新查询不再返回该分组；旧链接不会查询全部商品作为兜底。
- 动态装修只存分组编号。修改成员后，商城重新进入页面或刷新即可获取新商品；已打开页面不会主动接收后台推送。

## API 示例

```http
GET /admin-api/product/group/spu-filter-page?groupIds=10,20&categoryId=2&tabType=0&pageNo=1&pageSize=20
GET /admin-api/product/group/spu-filter-count?groupIds=10,20&categoryId=2
GET /admin-api/product/group/spu-filter-export?groupIds=10,20&categoryId=2&tabType=0
GET /admin-api/product/group/spu-group-map?spuIds=100,101

POST /admin-api/product/group/spu-batch-update
Content-Type: application/json

{"spuIds":[100,101],"groupIds":[10,20],"operation":"add"}
```

`operation` 为 `remove` 时，仅移除请求中的分组关系。所有数据按当前租户隔离；分页、状态计数和导出使用相同的筛选条件。原 `/product/spu/page` 与分类 Mapper 不加入分组关系查询。

商城公开接口为 `/app-api/product/group/list`、`list-by-ids`、`list-by-spu-id` 和 `spu-page`。商品详情查询只有在售且属于当前租户的商品才返回公开分组。

## 发布顺序

1. 已启用分组的数据库执行 `sql/mysql/product_group_operations.sql`。尚未创建分组表的数据库先执行 `sql/mysql/product_group.sql`。两份脚本均可重复执行；不会把已设为内部的分组重新改成公开。
2. 发布后端，再发布管理后台和商城。新后端读取 `storefront_visible` 字段，必须先完成数据库升级。
3. 使用实际角色检查商品查询、导出、分组更新权限。批量修改允许 `product:spu:update` 或 `product:group:update`；查询和导出沿用商品对应权限。
4. 在实际 MySQL 与微信开发者工具中验收公开/内部切换、分组页分享和小程序上传体积。H2 自动化测试不能替代真实 MySQL 迁移验收。

回滚旧客户端时，未提交 `groupIds` 的商品更新保留已有关系；分组更新未提交 `storefrontVisible` 时保留已有公开性设置。数据库新增字段可保留，勿通过删除关系表回滚客户端。

## 本轮验证记录（2026-09-19）

- Java 17：`mvn -pl yudao-module-mall/yudao-module-product-server -am clean test`，商品模块 50 项测试通过，依赖模块一并通过。涵盖真实 H2 查询、方法参数校验和 8 项双事务并发测试；先复现导出参数校验和分组删除并发问题，再验证修复。
- Node 24：`pnpm exec vitest run apps/web-antd/src/views/mall apps/web-antd/src/api/mall/product --maxWorkers=2`，47 个文件、216 项测试通过；`pnpm --filter @vben/web-antd typecheck` 和 `pnpm build:antd` 通过。
- 全工作区提交钩子的 `pnpm check:type` 被未改动的 `web-ele` 既有类型错误阻断；本次按仓库约定只修改并验收 `web-antd`。提交前单独执行相关格式、静态检查、类型检查、测试与构建，跳过这个包含其他 UI 应用的提交钩子。
- UniApp：全部 18 个 `test:*` 脚本通过，H5 和微信小程序生产构建通过。分组分享测试使用 UniApp 的实际 H5 查询解码函数验证特殊字符，不只检查链接字符串。
- 原分类 Controller/Service/Mapper、ProductSpuMapper、分类装修组件和商城分类页面无差异；Promotion、Trade 及跨服务商品 DTO 无差异。
- 独立只读 Review 未发现可复现的 P1/P2/P3 问题，并额外复跑后端 23 项聚焦测试及 UniApp 分组专项测试。
- 尚未执行真实 MySQL 增量脚本、连接真实后端的浏览器验收或微信真机测试。小程序主包文件块估算为 1746 KB，仓库预算为 1750 KB；应在微信开发者工具确认实际上传体积。
