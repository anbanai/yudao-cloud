-- 在商城生产库暂停租户 1 的顺丰下单、退货预约和账号编辑，等待在途请求完成后执行。
WITH source_waybills AS (
    SELECT tenant_id, account_id
    FROM trade_logistics_waybill
    WHERE tenant_id = 1 AND account_id = 2
    GROUP BY tenant_id, account_id
    HAVING COUNT(*) = 3
)
UPDATE trade_logistics_account AS src
JOIN trade_logistics_account AS dst
  ON dst.tenant_id = src.tenant_id AND dst.id = 1
JOIN source_waybills AS batch
  ON batch.tenant_id = src.tenant_id AND batch.account_id = src.id
JOIN trade_logistics_waybill AS w
  ON w.tenant_id = src.tenant_id AND w.account_id = src.id
SET w.account_id = 1,
    dst.default_flag = 1,
    src.default_flag = 0,
    src.deleted = 1
WHERE src.tenant_id = 1 AND src.id = 2
  AND src.deleted = 0 AND src.status = 0 AND src.default_flag = 1
  AND dst.deleted = 0 AND dst.status = 0 AND dst.default_flag = 0
  AND src.logistics_id = dst.logistics_id
  AND OCTET_LENGTH(src.partner_id) > 0 AND OCTET_LENGTH(src.check_word) > 0
  AND BINARY src.partner_id = BINARY dst.partner_id
  AND BINARY src.check_word = BINARY dst.check_word
  AND BINARY JSON_ARRAY(src.endpoint, src.service_code, src.template_code,
                        src.sender_name, src.sender_phone, src.sender_province,
                        src.sender_city, src.sender_district, src.sender_address,
                        src.default_weight_kg, src.paper_width_mm, src.paper_height_mm, src.dpi)
    = BINARY JSON_ARRAY(dst.endpoint, dst.service_code, dst.template_code,
                        dst.sender_name, dst.sender_phone, dst.sender_province,
                        dst.sender_city, dst.sender_district, dst.sender_address,
                        dst.default_weight_kg, dst.paper_width_mm, dst.paper_height_mm, dst.dpi)
  AND NOT EXISTS (
      SELECT 1 FROM trade_after_sale_return_shipment AS r
      WHERE r.tenant_id = src.tenant_id AND r.account_id = src.id
  );
