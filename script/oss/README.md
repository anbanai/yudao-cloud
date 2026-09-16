# OSS 历史图片元数据迁移

`migrate_aliyun_oss_image_metadata.py` 用 OSS 的同对象复制覆盖历史图片的响应头，不会下载或重新编码图片。脚本默认是 dry-run，只有显式传入 `--execute` 才会写入。

先安装依赖并设置凭据：

```bash
python3 -m pip install oss2
export ALIYUN_OSS_ENDPOINT=https://oss-cn-chengdu.aliyuncs.com
export ALIYUN_OSS_BUCKET=teaworthshare
export ALIYUN_ACCESS_KEY_ID='...'
export ALIYUN_ACCESS_KEY_SECRET='...'
```

先预览指定目录：

```bash
python3 script/oss/migrate_aliyun_oss_image_metadata.py --prefix public/
```

确认对象范围和数量无误后再执行。`--confirm-bucket` 是执行保护，必须与环境变量中的 Bucket 完全一致：

```bash
python3 script/oss/migrate_aliyun_oss_image_metadata.py \
  --prefix public/ \
  --execute \
  --confirm-bucket teaworthshare \
  --public-cache \
  --sleep-ms 50
```

脚本只迁移 JPEG、PNG、WebP、GIF 和 AVIF；SVG 等可能包含主动内容的格式会跳过。建议先用 `--max-objects 10` 小批量验证，并用 `curl -I` 检查 `Content-Type`、`Content-Disposition` 和 `Cache-Control`。不要把 AccessKey 写入脚本、仓库或命令历史；使用临时凭证或环境变量。
