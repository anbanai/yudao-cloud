package cn.iocoder.yudao.module.product.service.youzan;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static cn.iocoder.yudao.module.product.service.youzan.ImportPlan.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper.PLATFORM_MERCHANT_NAME;

@Service
public class YouzanImportService {
    // Match the catalog's platform merchant rule. The grant belongs to the operator's tenant.
    private static final String AUTHORIZED_MERCHANTS = """
            SELECT m.id, COALESCE(NULLIF(TRIM(m.store_name), ''), m.name) AS name
            FROM product_yz_import_grant g
            JOIN product_merchant m ON m.id = g.merchant_id
            WHERE g.tenant_id = ? AND g.operator_id = ? AND m.deleted = 0 AND m.status = ?
              AND (m.tenant_id = g.tenant_id
                   OR (m.tenant_id = 0 AND m.name = ? AND m.owner_account_id IS NULL))
            """;

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ProductSpuService spus;
    private final ProductSkuService skus;
    private final String encodedKey;

    public YouzanImportService(JdbcTemplate db, ObjectMapper json, ProductSpuService spus, ProductSkuService skus,
                              @Value("${yudao.product.youzan.preview-key:}") String encodedKey) {
        this.db = db; this.json = json; this.encodedKey = encodedKey;
        this.spus = spus; this.skus = skus;
    }

    public record Comparison(Integer price, Integer stock) {}
    public record ResultLine(Row row, String action, Comparison before, String bindingState) {}
    public record Batch(String batchId, String merchantId, boolean confirmed, List<ResultLine> lines, List<String> unusedColumns) {}
    public record MerchantOption(String id, String name) {}

    public List<MerchantOption> listAuthorizedMerchants() {
        return db.query(AUTHORIZED_MERCHANTS + "ORDER BY m.id",
                (rs, rowNum) -> new MerchantOption(rs.getString("id"), rs.getString("name")),
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                ProductMerchantStatusEnum.APPROVED.getStatus(), PLATFORM_MERCHANT_NAME);
    }

    private record Snapshot(List<Line> lines, List<String> unusedColumns) {}
    private Batch response(Scope scope, String id, boolean confirmed, List<Line> lines, List<String> unused) {
        Map<Key, String> states = new HashMap<>();
        if (confirmed && scope.merchant != null) {
            db.query("SELECT product_key,sku_key,mall_sku_id FROM product_yz_import_draft WHERE tenant_id=? AND merchant_id=? AND batch_id=?",
                    rs -> { states.put(new Key(rs.getString(1), rs.getString(2)), rs.getObject(3) == null ? "UNBOUND" : "BOUND"); },
                    scope.tenant, scope.merchant, id);
        }
        return new Batch(id, Objects.toString(scope.merchant, null), confirmed, lines.stream().map(line -> new ResultLine(line.row(), line.action(),
                line.before() == null ? null : new Comparison(line.before().price(), line.before().stock()),
                "PENDING".equals(line.action()) ? states.get(line.row().item().key()) : null)).toList(), unused);
    }
    private record Scope(long tenant, Long merchant, long operator) {
        String aad(String id) { return "product-yz:1:" + tenant + ":" + merchant + ":" + operator + ":" + id; }
    }
    private record Stored(Long merchant, byte[] bytes, boolean confirmed, boolean expired) {}

    private Scope scope(Long merchant, boolean lock) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        Long operator = SecurityFrameworkUtils.getLoginUserId();
        if (operator == null || (merchant != null && db.query(AUTHORIZED_MERCHANTS + "AND m.id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, rowNum) -> rs.getLong("id"), tenant, operator,
                ProductMerchantStatusEnum.APPROVED.getStatus(), PLATFORM_MERCHANT_NAME, merchant).isEmpty())) {
            throw new AccessDeniedException("商家不可用或当前账号没有该商家的商品导入授权");
        }
        return new Scope(tenant, merchant, operator);
    }

    private Existing lookup(Scope scope, Key key, boolean lock) {
        return db.query("""
                SELECT d.name,d.specification,d.source_price,d.source_stock,d.mapping_hints,d.mall_sku_id,
                  CASE WHEN d.mall_sku_id IS NULL THEN d.source_price ELSE s.price END AS price,
                  CASE WHEN d.mall_sku_id IS NULL THEN d.source_stock ELSE s.stock END AS stock
                FROM product_yz_import_draft d
                LEFT JOIN product_sku s ON s.id=d.mall_sku_id AND s.tenant_id=d.tenant_id AND s.deleted=0
                  AND EXISTS (SELECT 1 FROM product_spu p WHERE p.id=s.spu_id AND p.tenant_id=d.tenant_id AND p.merchant_id=d.merchant_id AND p.deleted=0)
                WHERE d.tenant_id=? AND d.merchant_id=? AND d.product_key=? AND d.sku_key=?
                """ + (lock ? " FOR UPDATE" : ""), rs -> {
            if (!rs.next()) return null;
            Map<String,String> hints;
            try { hints = json.readValue(rs.getString(5), new TypeReference<>() {}); }
            catch (java.io.IOException ex) { throw new IllegalStateException("商品草稿数据无效"); }
            return new Existing(new Item(key, rs.getString(1), rs.getString(2), rs.getObject(3, Integer.class), rs.getObject(4, Integer.class), hints),
                    rs.getObject(6, Long.class), rs.getObject(7, Integer.class), rs.getObject(8, Integer.class));
        }, scope.tenant, scope.merchant, key.product(), key.sku());
    }

    @Transactional(rollbackFor = Exception.class)
    public Batch preview(Long merchant, MultipartFile file) throws Exception {
        Scope scope = scope(merchant, false);
        List<Row> rows;
        try { rows = YouzanWorkbook.read(file); }
        catch (IllegalArgumentException ex) { throw new cn.iocoder.yudao.framework.common.exception.ServiceException(BAD_REQUEST.getCode(), ex.getMessage()); }
        return save(scope, rows, YouzanWorkbook.unused());
    }

    private List<Line> evaluateBatch(Scope scope, List<Row> rows, boolean lock) {
        if (scope.merchant == null) return evaluate(rows, key -> null);
        Map<String, Boolean> alreadyImported = new HashMap<>();
        return evaluate(rows, key -> lookup(scope, key, lock)).stream().map(line -> {
            if (!"PENDING".equals(line.action())) return line;
            String product = line.row().item().key().product();
            boolean exists = alreadyImported.computeIfAbsent(product, source -> !db.queryForList(
                    "SELECT sku_key FROM product_yz_import_draft WHERE tenant_id=? AND merchant_id=? AND product_key=? LIMIT 1" + (lock ? " FOR UPDATE" : ""),
                    String.class, scope.tenant, scope.merchant, source).isEmpty());
            return exists ? new Line(line.row(), "CONFLICT", null) : line;
        }).toList();
    }

    private Batch save(Scope scope, List<Row> rows, List<String> unused) throws Exception {
        var lines = evaluateBatch(scope, rows, false);
        String id = UUID.randomUUID().toString();
        byte[] payload = encode(scope, id, new Snapshot(lines, unused));
        db.update("INSERT INTO product_yz_import_batch(batch_id,tenant_id,merchant_id,operator_id,expires_at,row_count,payload) VALUES(?,?,?,?,TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP(6)),?,?)",
                id, scope.tenant, scope.merchant, scope.operator, rows.size(), payload);
        return response(scope, id, false, lines, unused);
    }

    private byte[] encode(Scope scope, String id, Snapshot snapshot) throws Exception {
        byte[] payload = new PreviewCipher(encodedKey).seal(scope.aad(id), json.writeValueAsBytes(snapshot));
        if (payload.length > 12_000_000) throw exception(BAD_REQUEST);
        return payload;
    }

    private Snapshot decode(Scope scope, String id, Stored stored) throws Exception {
        return json.readValue(new PreviewCipher(encodedKey).open(scope.aad(id), stored.bytes), Snapshot.class);
    }

    private Stored stored(String id, boolean lock) {
        Scope owner = scope(null, false);
        var batches = db.query("""
                SELECT merchant_id,payload,confirmed,expires_at<=NOW(6)
                FROM product_yz_import_batch WHERE tenant_id=? AND operator_id=? AND batch_id=?
                """ + (lock ? " FOR UPDATE" : ""),
                (rs, index) -> new Stored(rs.getObject(1, Long.class), rs.getBytes(2), rs.getBoolean(3), rs.getBoolean(4)),
                owner.tenant, owner.operator, id);
        if (batches.isEmpty()) throw new AccessDeniedException("批次不可访问");
        return batches.get(0);
    }

    private Batch load(Long merchant, UUID batchId, boolean confirm) throws Exception {
        String id = batchId.toString();
        Stored batch = stored(id, confirm);
        if (merchant != null && !merchant.equals(batch.merchant)) throw new AccessDeniedException("批次不可访问");
        Scope scope = scope(batch.merchant, confirm);
        if (!batch.confirmed && batch.expired) throw invalid("预览已过期，请重新上传");
        Snapshot snapshot = decode(scope, id, batch);
        var lines = snapshot.lines();
        if (confirm && !batch.confirmed) {
            int count = 0;
            if (scope.merchant != null) {
                var current = evaluateBatch(scope, lines.stream().map(Line::row).toList(), true);
                if (!current.equals(lines)) throw invalid("预览后数据已变化，请重新预览");
                count = insertDrafts(scope, id, lines);
            }
            db.update("UPDATE product_yz_import_batch SET confirmed=TRUE, confirmed_at=NOW(6),draft_count=? WHERE batch_id=? AND tenant_id=? AND operator_id=?",
                    count, id, scope.tenant, scope.operator);
        }
        return response(scope, id, confirm || batch.confirmed, lines, snapshot.unusedColumns());
    }

    private int insertDrafts(Scope scope, String id, List<Line> lines) throws Exception {
        int count = 0;
        for (Line line : lines) {
            if (!"PENDING".equals(line.action())) continue;
            var item = line.row().item();
            db.update("INSERT INTO product_yz_import_draft(tenant_id,merchant_id,product_key,sku_key,name,specification,source_price,source_stock,mapping_hints,batch_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    scope.tenant, scope.merchant, item.key().product(), item.key().sku(), item.name(), item.specification(), item.cents(), item.stock(), json.writeValueAsString(item.hints()), id);
            count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<String> pending(UUID before) {
        Scope owner = scope(null, false);
        String query = """
                SELECT batch_id FROM product_yz_import_batch
                WHERE tenant_id=? AND operator_id=? AND confirmed=TRUE AND merchant_id IS NULL
                """;
        String order = " ORDER BY batch_id DESC LIMIT 20";
        if (before == null) return db.queryForList(query + order, String.class, owner.tenant, owner.operator);
        return db.queryForList(query + " AND batch_id < ?" + order, String.class, owner.tenant, owner.operator, before.toString());
    }

    @Transactional(rollbackFor = Exception.class)
    public Batch assign(UUID batchId, long merchant) throws Exception {
        String id = batchId.toString();
        Stored batch = stored(id, true);
        Scope target = scope(merchant, true);
        if (!batch.confirmed) throw invalid("请先确认导入批次");
        if (batch.merchant != null && batch.merchant != merchant) throw invalid("该批次已分配至其他商家");
        Scope previous = new Scope(target.tenant, batch.merchant, target.operator);
        Snapshot snapshot = decode(previous, id, batch);
        if (batch.merchant == null) {
            var lines = evaluateBatch(target, snapshot.lines().stream().map(Line::row).toList(), true);
            snapshot = new Snapshot(lines, snapshot.unusedColumns());
            byte[] payload = encode(target, id, snapshot);
            int count = insertDrafts(target, id, lines);
            db.update("UPDATE product_yz_import_batch SET merchant_id=?,payload=?,draft_count=? WHERE batch_id=? AND tenant_id=? AND operator_id=?",
                    merchant, payload, count, id, target.tenant, target.operator);
        }
        return response(target, id, true, snapshot.lines(), snapshot.unusedColumns());
    }

    private static RuntimeException invalid(String text) {
        return new cn.iocoder.yudao.framework.common.exception.ServiceException(BAD_REQUEST.getCode(), text);
    }

    @Transactional(rollbackFor = Exception.class)
    public Batch confirm(Long merchant, UUID id) throws Exception { return load(merchant, id, true); }
    @Transactional(readOnly = true)
    public Batch result(Long merchant, UUID id) throws Exception { return load(merchant, id, false); }

    @Transactional(rollbackFor = Exception.class)
    public Long supplement(long merchant, UUID batchId, String product, Map<String,String> sourceByProperties, ProductSpuSaveReqVO form) {
        Scope scope = scope(merchant, true);
        if (db.queryForList("SELECT batch_id FROM product_yz_import_batch WHERE tenant_id=? AND merchant_id=? AND operator_id=? AND batch_id=? AND confirmed=TRUE", String.class, scope.tenant, merchant, scope.operator, batchId.toString()).isEmpty())
            throw new AccessDeniedException("批次不可访问");
        if (form.getId() != null || !Objects.equals(form.getMerchantId(), merchant) || form.getSkus() == null || form.getSkus().isEmpty())
            throw invalid("草稿或商家不匹配");
        if (form.getName() == null || form.getName().isBlank() || form.getName().length() > 255)
            throw invalid("请补充有效的商品名称");
        List<String> keys = db.queryForList("SELECT sku_key FROM product_yz_import_draft WHERE tenant_id=? AND merchant_id=? AND product_key=? ORDER BY sku_key FOR UPDATE",
                String.class, scope.tenant, merchant, product);
        List<String> owned = db.queryForList("SELECT sku_key FROM product_yz_import_draft WHERE tenant_id=? AND merchant_id=? AND product_key=? AND batch_id=? ORDER BY sku_key", String.class, scope.tenant, merchant, product, batchId.toString());
        if (!keys.equals(owned)) throw invalid("同商品规格分属多个批次，请核对后人工处理");
        if (keys.isEmpty() || sourceByProperties.size() != keys.size() || form.getSkus().size() != keys.size()
                || !new HashSet<>(keys).equals(new HashSet<>(sourceByProperties.values()))) throw invalid("请为商品的每个来源规格明确选择商城规格");
        Set<String> used = new HashSet<>();
        for (var sku : form.getSkus()) {
            String signature = !Boolean.TRUE.equals(form.getSpecType()) || sku.getProperties() == null ? "" : String.join(",", sku.getProperties().stream().map(p -> p.getPropertyId() + ":" + p.getValueId()).sorted().toList());
            String source = sourceByProperties.get(signature);
            if (source == null || !used.add(source)) throw invalid("来源规格映射不唯一");
            Existing draft = lookup(scope, new Key(product, source), true);
            if (draft == null || draft.mallSku() != null) throw invalid("商品已处理，请通过商城商品编辑");
            boolean presale = List.of("商品类型", "售卖方式", "商品状态").stream()
                    .anyMatch(field -> "预售".equals(draft.source().hints().get(field)) || "预售商品".equals(draft.source().hints().get(field)));
            if (presale && (!Boolean.TRUE.equals(form.getIsPreSale()) || form.getDeliveryDays() == null || form.getDeliveryDays() < 1))
                throw invalid("请补充来源预售商品的发货周期");
            if (draft.price() != null) sku.setPrice(draft.price());
            if (draft.stock() != null) sku.setStock(draft.stock());
            if (sku.getPrice() == null || sku.getStock() == null || sku.getPrice() < 0 || sku.getStock() < 0)
                throw invalid("请补充有效的规格价格和库存");
        }
        validateDelivery(scope, form);
        form.setSalesCount(0); form.setBrowseCount(0); form.setVirtualSalesCount(0);
        Long spu = spus.createUnlistedSpu(form);
        var created = skus.getSkuListBySpuId(spu);
        if (created.size() != keys.size()) throw invalid("规格绑定失败");
        for (var sku : created) {
            String signature = !Boolean.TRUE.equals(form.getSpecType()) || sku.getProperties() == null ? "" : String.join(",", sku.getProperties().stream().map(p -> p.getPropertyId() + ":" + p.getValueId()).sorted().toList());
            if (db.update("UPDATE product_yz_import_draft SET mall_sku_id=? WHERE tenant_id=? AND merchant_id=? AND product_key=? AND sku_key=? AND mall_sku_id IS NULL",
                    sku.getId(), scope.tenant, merchant, product, sourceByProperties.get(signature)) != 1) throw invalid("规格绑定失败");
        }
        return spu;
    }

    private void validateDelivery(Scope scope, ProductSpuSaveReqVO form) {
        List<Integer> types = form.getDeliveryTypes();
        if (types == null || types.isEmpty() || new HashSet<>(types).size() != types.size()
                || types.stream().anyMatch(type -> type == null || (type != 1 && type != 2))) {
            throw invalid("请选择合法的配送方式：快递或自提");
        }
        if (types.contains(1)) {
            Long template = form.getDeliveryTemplateId();
            if (template == null || template <= 0 || db.queryForList(
                    "SELECT id FROM trade_delivery_express_template WHERE id=? AND tenant_id=? AND deleted=0 AND charge_mode IN (1,2,3) FOR UPDATE",
                    Long.class, template, scope.tenant).isEmpty()) {
                throw invalid("快递配送需要本租户有效的运费模板");
            }
        } else {
            form.setDeliveryTemplateId(null);
        }
    }
}
