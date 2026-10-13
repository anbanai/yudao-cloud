# 企微客服图片配置部署

客服图片配置使用 `trade_config.kefu_wechat_qr_code_url` 保存。全量初始化数据库时，字段已包含在 `db/yudao.sql` 中；已有环境发布后端前，请在对应 MySQL 数据库执行可重复运行的增量脚本：

```sql
source sql/mysql/trade_kefu_wechat_qr_code.sql;
```

确认字段创建完成后再启动或滚动更新交易服务。后台进入商城交易配置的“客服”页签上传企微二维码或企微联系人图片并保存，小程序会优先展示该图片，图片缺失或加载失败时回退在线客服。
