# 商品分组三端运营接入

目标：保持原商品主分类、营销适用商品清单和结算规则，打通后台管理、通用选品、装修与商城公开分组。

## 已确认决策

- 人工平级多分组；商品仍必填单一主分类。
- 营销选品可以按分组查找，但活动保存明确的商品/SKU 清单。
- 分组新增 storefrontVisible，区分商城公开与内部运营；既有数据迁移为公开。
- 商品列表和通用选择器新增分组（任一匹配）与未分组筛选；与原分类、名称、日期、状态取交集。
- 商品批量操作只支持追加/移出指定分组，事务完成；不覆盖其他归属。
- 商品栏支持 manual/group 数据源，旧数据缺省 manual；动态分组只保存分组 ID，15 组、50 个商品上限延续原有约束。
- 原 ProductCategory、ProductGroup 以及旧装修迁移保留。图片、导航、文字等通过统一链接选择器选择公开分组落地页。

## 接口与数据流

- 管理端独立 `/product/group/spu-filter-page`、`spu-filter-count`、`spu-filter-export`：原 SPU 请求字段加 groupIds 或 ungrouped，不改原分类 SQL。
- `/product/group/spu-group-map?spuIds` 批量返回商品分组归属，避免逐商品查询。
- POST `/product/group/spu-batch-update`：spuIds（最多 200）、groupIds（最多 15）、operation（add/remove）。查询用 product:spu:query，修改用 product:spu:update 或 product:group:update。
- 分组分页增加 spuCount/saleSpuCount，精简列表包含 storefrontVisible。
- App `/product/group/list`、`list-by-spu-id` 返回启用且公开分组；原 list-by-ids/spu-page 同样校验公开性。
- UniApp 新增 group-index 浏览入口，group-list 提供搜索/排序/公开分组筛选/分享及请求重试；商品详情可点击公开分组继续浏览。

## 验证与上线

- 后端验证交集过滤、无分组、去重、计数/导出一致、租户隔离、追加/移出事务和重入、公开性、旧客户端 null 行为。
- Vben 验证查询路由、选品跨页、批量 payload、手选/动态切换、装修旧数据兼容、链接编解码及失效提示。
- UniApp 验证数据源隔离、旧响应不覆盖新请求、翻页失败同页重试、内部/禁用分组过滤和购物路径。
- Maven 商品模块测试、Vben 专项 Vitest/lint/类型检查/构建、UniApp 专项脚本/H5/微信构建。
- 数据库先执行幂等增量 SQL，再发布后端、后台与小程序。现有分组公开性保持不变。
- 使用三个隔离 worktree；主工作区商品、订单及 OSS 未提交改动不得纳入本次提交。
