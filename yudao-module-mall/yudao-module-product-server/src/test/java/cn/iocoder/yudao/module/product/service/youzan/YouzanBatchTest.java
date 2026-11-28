package cn.iocoder.yudao.module.product.service.youzan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class YouzanBatchTest {
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private YouzanImportService service;
    private static final List<String> HEADERS = List.of(("商品id|规格id|商品名称|商品规格|商品规格名称|商品编码|规格编码|商品条码|规格条码|商品类型|售卖方式|商品分组|分享描述|商品卖点|价格（元）|成本价（元）|库存|累计销量|会员折扣|商品链接|供货价（元）|供货商|最低零售价（元）|最高零售价（元）|进口税费|海关税率（%）|跨境方式|创建时间|划线价（元）|访客数|浏览量|补货提醒数|商品状态|一级类目|二级类目|三级类目|四级类目|五级类目|类目参数|运费模版|配送方式|售后服务|库存扣减方式|商品页模版|重量（kg）|电子卡券有效期|限购周期及数量|开售时间|商品属性|商品定时下架|优惠券名称|名称备注").split("\\|"));

    @BeforeEach void setup() {
        String port = System.getenv("CMP186_PRODUCT_MYSQL_PORT");
        if (port != null) {
            if (!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException("fixture port");
            var nativeDs = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port
                    + "/cmp186_ephemeral?useSSL=false&allowPublicKeyRetrieval=true", "product_fixture", System.getenv("CMP186_PRODUCT_PASSWORD"));
            db = new JdbcTemplate(nativeDs);
            tx = new TransactionTemplate(new DataSourceTransactionManager(nativeDs));
            initializeServices();
            return;
        }
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("CREATE TABLE product_yz_import_grant(tenant_id BIGINT,merchant_id BIGINT,operator_id BIGINT)");
        db.execute("CREATE TABLE product_merchant(id BIGINT,tenant_id BIGINT,deleted INT,name VARCHAR,store_name VARCHAR,status INT,owner_account_id BIGINT)");
        db.execute("CREATE TABLE product_spu(id BIGINT,merchant_id BIGINT,tenant_id BIGINT,deleted INT)");
        db.execute("CREATE TABLE product_sku(id BIGINT,spu_id BIGINT,tenant_id BIGINT,price INT,stock INT,deleted INT)");
        db.execute("CREATE TABLE trade_delivery_express_template(id BIGINT,tenant_id BIGINT,charge_mode INT,deleted INT)");
        db.update("INSERT INTO trade_delivery_express_template VALUES(10,1,1,0),(11,5,1,0),(12,1,1,1),(13,1,9,0)");
        db.execute("CREATE TABLE product_yz_import_batch(batch_id VARCHAR PRIMARY KEY,tenant_id BIGINT, merchant_id BIGINT,operator_id BIGINT,confirmed BOOLEAN DEFAULT FALSE,all_products_published BOOLEAN DEFAULT FALSE,expires_at TIMESTAMP,confirmed_at TIMESTAMP,row_count INT,draft_count INT,payload BLOB)");
        db.execute("CREATE TABLE product_yz_import_item(tenant_id BIGINT,batch_id VARCHAR,product_key VARCHAR,payload BLOB NOT NULL,merchant_id BIGINT,spu_id BIGINT,PRIMARY KEY(tenant_id,batch_id,product_key))");
        db.execute("CREATE TABLE product_yz_import_draft(tenant_id BIGINT,merchant_id BIGINT,product_key VARCHAR,sku_key VARCHAR,name VARCHAR,specification VARCHAR,source_price INT,source_stock INT,mapping_hints VARCHAR,mall_sku_id BIGINT,batch_id VARCHAR,PRIMARY KEY(tenant_id,merchant_id,product_key,sku_key))");
        db.update("INSERT INTO product_merchant VALUES (2,1,0,'一号商家','一号店',20,NULL),(3,1,0,'二号商家',NULL,20,NULL),(2,5,0,'其他租户',NULL,20,NULL)");
        db.update("INSERT INTO product_yz_import_grant VALUES(1,2,7),(1,2,8),(1,3,7),(5,2,7)");
        initializeServices();
    }

    private void initializeServices() {
        service = new YouzanImportService(db, new ObjectMapper(), null, null, Base64.getEncoder().encodeToString(new byte[32]));
        login(1, 7);
    }

    private void login(long tenant, long operator) {
        TenantContextHolder.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(operator), null, List.of()));
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void listsOnlyAvailableMerchantsGrantedToTheCurrentOperator() {
        db.update("INSERT INTO product_merchant VALUES (4,1,1,'已删除',NULL,20,NULL),(5,1,0,'已冻结',NULL,40,NULL)");
        db.update("INSERT INTO product_yz_import_grant VALUES (1,4,8),(1,5,8),(1,6,8)");
        login(1, 8);
        assertEquals(List.of(new YouzanImportService.MerchantOption("2", "一号店")),
                service.listAuthorizedMerchants());
        login(5, 7);
        assertEquals(List.of(new YouzanImportService.MerchantOption("2", "其他租户")),
                service.listAuthorizedMerchants());
        login(1, 99);
        assertEquals(List.of(), service.listAuthorizedMerchants());
    }

    @Test void authorizedPlatformMerchantSupportsImportAndBindingInTheOperatorsTenant() throws Exception {
        db.update("INSERT INTO product_merchant VALUES (1,0,0,'平台自营商家','平台自营',20,NULL)");
        db.update("INSERT INTO product_yz_import_grant VALUES (1,1,7),(5,1,7)");
        installSyntheticCatalog();

        assertTrue(service.listAuthorizedMerchants().contains(new YouzanImportService.MerchantOption("1", "平台自营")));
        UUID id = UUID.fromString(singleSku(1L, "S1").batchId());
        login(1, 8);
        assertThrows(AccessDeniedException.class, () -> confirm(1, id));
        login(5, 7);
        assertThrows(AccessDeniedException.class, () -> confirm(1, id));

        login(1, 7);
        assertEquals("UNBOUND", confirm(1, id).lines().get(0).bindingState());
        var form = singleSkuForm().setMerchantId(1L);
        Long spu = tx.execute(status -> service.supplement(1, id, "P", Map.of("", "S1"), form));
        assertEquals(201L, spu);
        assertEquals("BOUND", service.result(1L, id).lines().get(0).bindingState());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_spu WHERE tenant_id=1 AND merchant_id=1", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft WHERE tenant_id=1 AND merchant_id=1", Integer.class));
    }

    @Test void importRejectsGrantsToOtherTenantsAndNonPlatformGlobalMerchants() throws Exception {
        db.update("""
                INSERT INTO product_merchant VALUES
                    (4,5,0,'其他租户',NULL,20,NULL),
                    (5,0,0,'其他全局商家',NULL,20,NULL),
                    (6,0,0,'平台自营商家',NULL,20,89),
                    (7,0,0,'平台自营商家',NULL,40,NULL),
                    (8,0,1,'平台自营商家',NULL,20,NULL)
                """);
        db.update("INSERT INTO product_yz_import_grant VALUES (1,4,8),(1,5,8),(1,6,8),(1,7,8),(1,8,8)");
        login(1, 8);
        assertEquals(List.of(new YouzanImportService.MerchantOption("2", "一号店")), service.listAuthorizedMerchants());
        var workbook = file("Sheet1", false);
        for (long merchant = 4; merchant <= 8; merchant++) {
            long deniedMerchant = merchant;
            assertThrows(AccessDeniedException.class, () -> service.preview(deniedMerchant, workbook));
        }
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_batch", Integer.class));
    }

    private MockMultipartFile file(String sheetName, boolean wrongHeader) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(sheetName);
            var header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) header.createCell(index).setCellValue(HEADERS.get(wrongHeader && index < 2 ? 1 - index : index));
            for (int index = 0; index < 9; index++) {
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue("00" + index / 2);
                row.createCell(1).setCellValue("00" + index);
                row.createCell(2).setCellValue("合成茶");
                row.createCell(3).setCellValue("规格" + index);
                row.createCell(6).setCellValue("0001");
                row.createCell(10).setCellValue("预售");
                row.createCell(14).setCellValue(10.08);
                row.createCell(16).setCellValue(index);
                row.createCell(17).setCellValue(17);
                row.createCell(19).setCellValue("https://example.invalid/item");
                row.createCell(21).setCellValue("合成商城");
                row.createCell(29).setCellValue(999);
                row.createCell(30).setCellValue(888);
                row.createCell(32).setCellValue("仓库中");
            }
            workbook.write(output);
            return new MockMultipartFile("file", "test.xlsx", null, output.toByteArray());
        }
    }

    private YouzanImportService.Batch preview() throws Exception {
        return service.preview(2L, file("Sheet1", false));
    }

    private YouzanImportService.Batch singleSku(String sourceSku) throws Exception {
        return singleSku(2L, sourceSku);
    }

    private YouzanImportService.Batch singleSku(Long merchant, String sourceSku) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Sheet1");
            var header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) header.createCell(index).setCellValue(HEADERS.get(index));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("P"); row.createCell(1).setCellValue(sourceSku);
            row.createCell(2).setCellValue("合成商品"); row.createCell(14).setCellValue("8.25"); row.createCell(16).setCellValue(7);
            book.write(bytes);
            return service.preview(merchant, new MockMultipartFile("file", "synthetic.xlsx", null, bytes.toByteArray()));
        }
    }

    private ProductSpuSaveReqVO singleSkuForm() {
        return new ProductSpuSaveReqVO().setMerchantId(2L).setName("合成商品").setCategoryId(3L).setBrandId(4L)
                .setPicUrl("/synthetic.png").setDeliveryTypes(List.of(2)).setSpecType(false)
                .setSkus(List.of(new ProductSkuSaveReqVO()));
    }

    private void installSyntheticCatalog() {
        var spus = mock(ProductSpuService.class);
        var skus = mock(ProductSkuService.class);
        when(spus.createUnlistedSpu(any())).thenAnswer(call -> {
            ProductSpuSaveReqVO form = call.getArgument(0);
            db.update("INSERT INTO product_spu VALUES(201,?,?,0)", form.getMerchantId(), TenantContextHolder.getRequiredTenantId());
            db.update("INSERT INTO product_sku VALUES(202,201,1,?,?,0)", form.getSkus().get(0).getPrice(), form.getSkus().get(0).getStock());
            return 201L;
        });
        when(skus.getSkuListBySpuId(201L)).thenReturn(List.of(new ProductSkuDO().setId(202L)));
        service = new YouzanImportService(db, new ObjectMapper(), spus, skus, Base64.getEncoder().encodeToString(new byte[32]));
    }

    private Long bind(UUID id, ProductSpuSaveReqVO form) {
        return tx.execute(status -> service.supplement(2, id, "P", Map.of("", "S1"), form));
    }

    @Test void additionalSkuInOtherBatchDoesNotBlockOriginalOperator() throws Exception {
        installSyntheticCatalog();
        UUID first = UUID.fromString(singleSku("S1").batchId());
        login(1, 8);
        UUID early = UUID.fromString(singleSku("S2").batchId());
        login(1, 7);
        confirm(2, first);
        login(1, 8);
        assertThrows(ServiceException.class, () -> confirm(2, early));
        var later = singleSku("S2");
        assertEquals("CONFLICT", later.lines().get(0).action());
        assertNull(confirm(2, UUID.fromString(later.batchId())).lines().get(0).bindingState());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        assertThrows(AccessDeniedException.class, () -> bind(first, singleSkuForm()));
        login(1, 7);
        assertEquals(201L, bind(first, singleSkuForm()));
    }

    @Test void bindingIsLiveButConfirmationActionAndInventoryAreStable() throws Exception {
        installSyntheticCatalog();
        var preview = singleSku("S1");
        UUID id = UUID.fromString(preview.batchId());
        assertNull(preview.lines().get(0).bindingState());
        assertEquals("UNBOUND", confirm(2, id).lines().get(0).bindingState());
        bind(id, singleSkuForm());
        var result = service.result(2L, id);
        assertEquals("PENDING", result.lines().get(0).action());
        assertEquals("BOUND", result.lines().get(0).bindingState());
        assertEquals(result, confirm(2, id));
        assertEquals(result, service.result(2L, id));
        assertThrows(ServiceException.class, () -> bind(id, singleSkuForm()));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_sku", Integer.class));
        assertEquals(7, db.queryForObject("SELECT stock FROM product_sku", Integer.class));
        assertEquals(825, db.queryForObject("SELECT price FROM product_sku", Integer.class));
    }

    @Test void invalidDeliveryKeepsDraftUntilExpressTemplateIsValid() throws Exception {
        installSyntheticCatalog();
        UUID id = UUID.fromString(singleSku("S1").batchId());
        confirm(2, id);
        var form = singleSkuForm().setDeliveryTemplateId(10L);
        for (var invalid : List.of(List.<Integer>of(), List.of(3), List.of(1, 3), List.of(1, 1))) {
            form.setDeliveryTypes(invalid);
            assertThrows(ServiceException.class, () -> bind(id, form));
        }
        form.setDeliveryTypes(List.of(1, 2));
        for (Long invalid : Arrays.asList(null, -1L, 999L, 11L, 12L, 13L)) {
            form.setDeliveryTemplateId(invalid);
            assertThrows(ServiceException.class, () -> bind(id, form));
        }
        assertEquals("UNBOUND", service.result(2L, id).lines().get(0).bindingState());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_sku", Integer.class));
        form.setDeliveryTemplateId(10L);
        tx.executeWithoutResult(status -> {
            assertEquals(201L, bind(id, form));
            status.setRollbackOnly();
        });
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_sku", Integer.class));
        assertEquals("UNBOUND", service.result(2L, id).lines().get(0).bindingState());
        tx.executeWithoutResult(status -> {
            form.setDeliveryTypes(List.of(2)).setDeliveryTemplateId(999L);
            assertEquals(201L, bind(id, form));
            assertNull(form.getDeliveryTemplateId());
            status.setRollbackOnly();
        });
        assertEquals(201L, bind(id, form.setDeliveryTypes(List.of(1, 2)).setDeliveryTemplateId(10L)));
        assertEquals("BOUND", service.result(2L, id).lines().get(0).bindingState());
    }

    private YouzanImportService.Batch confirm(long merchant, UUID id) {
        return tx.execute(status -> {
            try { return service.confirm(merchant, id); }
            catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    private YouzanImportService.Batch assign(UUID id, long merchant) {
        return tx.execute(status -> {
            try { return service.assign(id, merchant); }
            catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    @Test void legacyUnassignedItemsCanBeEditedWithoutAMerchantAndReopenedAfterExpiry() throws Exception {
        login(1, 99);
        UUID id = UUID.fromString(singleSku(null, "S1").batchId());
        ProductSpuSaveReqVO partial = new ProductSpuSaveReqVO().setName("已编辑名称");
        assertThrows(ServiceException.class, () -> service.saveDraft(id, "P", partial, Map.of()));
        var confirmed = service.confirm(null, id);
        assertEquals("UNBOUND", confirmed.lines().get(0).bindingState());
        db.update("UPDATE product_yz_import_batch SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE batch_id=?", id.toString());
        assertEquals(new YouzanImportService.ItemDraft(null, Map.of(), null, null), service.draft(id, "P"));
        assertThrows(ServiceException.class, () -> service.saveDraft(id, "other", partial, Map.of()));
        assertThrows(ServiceException.class, () -> service.saveDraft(id, "P", partial, Map.of("", "wrong SKU")));
        assertThrows(ServiceException.class, () -> service.saveDraft(id, "P", singleSkuForm(), Map.of()));
        service.saveDraft(id, "P", partial, Map.of());
        var saved = service.saveDraft(id, "P", partial.setDescription("草稿详情"), Map.of("", "S1"));
        assertEquals(saved, service.draft(id, "P"));
        assertNull(saved.merchantId());
        assertEquals(confirmed, service.result(null, id));
        assertEquals(List.of(id.toString()), service.pending(null));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        assertThrows(AccessDeniedException.class, () -> publish(id, "P", 2, singleSkuForm(), "S1"));
        for (int[] other : List.of(new int[]{1, 8}, new int[]{5, 99})) {
            login(other[0], other[1]);
            assertTrue(service.pending(null).isEmpty());
            assertThrows(AccessDeniedException.class, () -> service.draft(id, "P"));
            assertThrows(AccessDeniedException.class, () -> service.saveDraft(id, "P", partial, Map.of()));
            assertThrows(AccessDeniedException.class, () -> publish(id, "P", 2, singleSkuForm(), "S1"));
        }
    }

    private String publish(UUID id, String product, long merchant, ProductSpuSaveReqVO form, String sourceSku) {
        return tx.execute(status -> {
            try { return service.publish(id, product, merchant, form, Map.of("", sourceSku)); }
            catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    private ProductSpuService installPublishCatalog() {
        var spus = mock(ProductSpuService.class);
        var skus = mock(ProductSkuService.class);
        when(spus.createSpu(any())).thenAnswer(call -> {
            ProductSpuSaveReqVO form = call.getArgument(0);
            long spu = db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class) + 201L;
            db.update("INSERT INTO product_spu VALUES(?,?,1,0)", spu, form.getMerchantId());
            db.update("INSERT INTO product_sku VALUES(?,?,1,?,?,0)", spu + 100, spu, form.getSkus().get(0).getPrice(), form.getSkus().get(0).getStock());
            return spu;
        });
        when(skus.getSkuListBySpuId(anyLong())).thenAnswer(call -> List.of(new ProductSkuDO().setId(call.<Long>getArgument(0) + 100)));
        service = new YouzanImportService(db, new ObjectMapper(), spus, skus, Base64.getEncoder().encodeToString(new byte[32]));
        return spus;
    }

    @Test void publishingBindsOnlySelectedProductAndCanUseDifferentMerchantsWithinOneBatch() throws Exception {
        var spus = installPublishCatalog();
        UUID id;
        try (var book = new XSSFWorkbook(file("Sheet1", false).getInputStream()); var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            for (int i = 9; i > 2; i--) sheet.removeRow(sheet.getRow(i));
            sheet.getRow(2).getCell(0).setCellValue("Second");
            book.write(out);
            id = UUID.fromString(service.preview(null, new MockMultipartFile("file", "products.xlsx", null, out.toByteArray())).batchId());
        }
        service.confirm(null, id);
        var form = singleSkuForm().setMerchantId(null).setIsPreSale(true).setDeliveryDays(3);
        form.getSkus().get(0).setPrice(1560).setStock(12);
        service.saveDraft(id, "000", form, Map.of("", "000"));
        assertThrows(ServiceException.class, () -> assign(id, 2));
        assertThrows(ServiceException.class, () -> publish(id, "000", 2, form, "wrong"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        tx.executeWithoutResult(status -> {
            assertEquals("201", publish(id, "000", 2, form, "000"));
            status.setRollbackOnly();
        });
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM product_spu", Integer.class));
        assertNull(service.draft(id, "000").spuId());
        assertEquals("201", publish(id, "000", 2, form, "000"));
        assertEquals("201", publish(id, "000", 2, form, "000"));
        assertThrows(ServiceException.class, () -> publish(id, "000", 3, form, "000"));
        assertThrows(ServiceException.class, () -> service.saveDraft(id, "000", form.setMerchantId(null), Map.of()));
        assertEquals("2", service.draft(id, "000").merchantId());
        assertNull(service.draft(id, "Second").merchantId());
        assertEquals(List.of("BOUND", "UNBOUND"), service.result(null, id).lines().stream().map(YouzanImportService.ResultLine::bindingState).toList());
        assertEquals(1560, db.queryForObject("SELECT price FROM product_sku", Integer.class));
        assertEquals(12, db.queryForObject("SELECT stock FROM product_sku", Integer.class));
        assertEquals(List.of(id.toString()), service.pending(null));
        assertEquals("202", publish(id, "Second", 3, form, "001"));
        assertTrue(service.pending(null).isEmpty());
        assertEquals(List.of(2L, 3L), db.queryForList("SELECT merchant_id FROM product_spu ORDER BY id", Long.class));
        assertNull(service.result(null, id).merchantId());
        verify(spus, never()).createUnlistedSpu(any());
    }

    @Test void publishingChecksCurrentTargetBindingsAndKeepsBothDraftsIntactOnConflict() throws Exception {
        var spus = installPublishCatalog();
        UUID id = UUID.fromString(singleSku(null, "S1").batchId());
        service.confirm(null, id);
        var form = singleSkuForm().setMerchantId(null);
        service.saveDraft(id, "P", form, Map.of("", "S1"));
        UUID assigned = UUID.fromString(singleSku("S1").batchId());
        var existing = confirm(2, assigned);
        assertEquals(existing, assign(assigned, 2));
        assertThrows(ServiceException.class, () -> publish(id, "P", 2, form, "S1"));
        verifyNoInteractions(spus);
        assertEquals("UNBOUND", service.result(null, id).lines().get(0).bindingState());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        assertNull(service.draft(id, "P").spuId());
        assertEquals("201", publish(id, "P", 3, form, "S1"));
    }

    @Test void pendingListPagesOnlyConfirmedUnassignedBatchesOfTheOwner() {
        List<String> expected = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            String id = new UUID(0, index).toString();
            int tenant = index == 24 ? 5 : 1;
            int operator = index == 23 ? 8 : 7;
            Long merchant = index == 22 ? 2L : null;
            boolean confirmed = index != 25;
            db.update("""
                    INSERT INTO product_yz_import_batch(batch_id,tenant_id,merchant_id,operator_id,confirmed,expires_at,row_count,payload)
                    VALUES(?,?,?,?,?,NOW(),0,?)
                    """, id, tenant, merchant, operator, confirmed, new byte[]{1});
            if (index <= 21) expected.add(id);
        }
        expected.sort(Comparator.reverseOrder());
        var first = service.pending(null);
        assertEquals(expected.subList(0, 20), first);
        assertEquals(expected.subList(20, 21), service.pending(UUID.fromString(first.get(19))));
    }

    @Test void concurrentPublicationCreatesOneSpuAndOneSetOfBindings() throws Exception {
        var spus = installPublishCatalog();
        UUID id = UUID.fromString(singleSku(null, "S1").batchId());
        service.confirm(null, id);
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        Callable<String> submit = () -> {
            login(1, 7);
            try { barrier.await(5, TimeUnit.SECONDS); return publish(id, "P", 2, singleSkuForm(), "S1"); }
            finally { cleanup(); }
        };
        try {
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
            verify(spus).createSpu(any());
        } finally { pool.shutdownNow(); }
    }

    @Test void staleAndExpiredPreviewsCannotWriteAndRepeatedConfirmationIsIdempotent() throws Exception {
        UUID id = UUID.fromString(preview().batchId());
        UUID stale = UUID.fromString(preview().batchId());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM product_yz_import_draft", Integer.class));
        assertTrue(confirm(2, id).confirmed());
        assertEquals(confirm(2, id), service.result(2L, id));
        assertThrows(ServiceException.class, () -> confirm(2, stale));
        var again = preview();
        assertTrue(again.lines().stream().allMatch(line -> line.action().equals("DUPLICATE")));
        db.update("UPDATE product_yz_import_batch SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE batch_id=?", again.batchId());
        assertThrows(ServiceException.class, () -> confirm(2, UUID.fromString(again.batchId())));
        assertEquals(9, db.queryForObject("SELECT count(*) FROM product_yz_import_draft", Integer.class));
        assertEquals(5, db.queryForObject("SELECT count(DISTINCT product_key) FROM product_yz_import_draft", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM product_spu", Integer.class));
    }

    @Test void differingLivePriceAndStockRemainUnchangedAfterConfirmation() throws Exception {
        confirm(2, UUID.fromString(preview().batchId()));
        db.update("INSERT INTO product_spu VALUES(1,2,1,0)");
        db.update("INSERT INTO product_sku VALUES(1,1,1,707,8,0)");
        db.update("UPDATE product_yz_import_draft SET mall_sku_id=1 WHERE sku_key='000'");
        var batch = preview();
        var difference = batch.lines().get(0);
        assertEquals("DIFFERENCE", difference.action());
        assertEquals(707, difference.before().price());
        assertEquals(8, difference.before().stock());
        confirm(2, UUID.fromString(batch.batchId()));
        assertEquals(707, db.queryForObject("SELECT price FROM product_sku", Integer.class));
        assertEquals(8, db.queryForObject("SELECT stock FROM product_sku", Integer.class));
    }

    @Test void scopeAndTemplateErrorsWriteNothing() throws Exception {
        assertThrows(ServiceException.class, () -> service.preview(2L, file("Other", false)));
        assertThrows(ServiceException.class, () -> service.preview(2L, file("sheet1", false)));
        assertThrows(ServiceException.class, () -> service.preview(2L, file("Sheet1", true)));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM product_yz_import_batch", Integer.class));
        UUID id = UUID.fromString(preview().batchId());
        assertThrows(AccessDeniedException.class, () -> confirm(3, id));
        login(1, 8);
        assertThrows(AccessDeniedException.class, () -> service.result(2L, id));
        login(5, 7);
        assertThrows(AccessDeniedException.class, () -> confirm(2, id));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM product_yz_import_draft", Integer.class));
    }

    @Test void downloadedTemplateMatchesAll52OriginalHeadersWithoutData() throws Exception {
        var output = new ByteArrayOutputStream();
        YouzanWorkbook.template(output);
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(1, book.getNumberOfSheets());
            var sheet = book.getSheet("Sheet1");
            assertEquals(1, sheet.getPhysicalNumberOfRows());
            assertEquals(52, sheet.getRow(0).getLastCellNum());
            for (int index = 0; index < 52; index++) assertEquals(HEADERS.get(index), sheet.getRow(0).getCell(index).getStringCellValue());
            assertEquals("@", sheet.getColumnStyle(6).getDataFormatString());
        }
        var batch = preview();
        var item = batch.lines().get(0).row().item();
        assertEquals(1008, item.cents());
        assertEquals(0, item.stock());
        assertEquals(Map.of("规格编码", "0001", "售卖方式", "预售", "商品状态", "仓库中"), item.hints());
        assertTrue(batch.unusedColumns().containsAll(List.of("商品链接", "供货商", "累计销量", "访客数", "浏览量")));
        assertEquals("PENDING", batch.lines().get(0).action());
    }

    @Test void concurrentConfirmationCreatesOnlyNineDrafts() throws Exception {
        UUID id = UUID.fromString(preview().batchId());
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        Callable<YouzanImportService.Batch> submit = () -> {
            login(1, 7);
            try { barrier.await(5, TimeUnit.SECONDS); return confirm(2, id); }
            finally { cleanup(); }
        };
        try {
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(9, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
            assertEquals(9, db.queryForObject("SELECT draft_count FROM product_yz_import_batch", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void confirmingAnInvalidWorkbookCreatesNoPendingDraftBatch() throws Exception {
        try (var book = new XSSFWorkbook(file("Sheet1", false).getInputStream()); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) sheet.getRow(i).getCell(0).setBlank();
            book.write(output);
            var preview = service.preview(null, new MockMultipartFile("file", "invalid.xlsx", null, output.toByteArray()));
            assertTrue(service.confirm(null, UUID.fromString(preview.batchId())).confirmed());
            assertTrue(service.pending(null).isEmpty());
        }
    }

    @Test void missingSourceIdsAreNeverReplacedByNamesCodesOrRowNumbers() throws Exception {
        try (var book = new XSSFWorkbook(file("Sheet1", false).getInputStream()); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(1).getCell(0).setBlank();
            sheet.getRow(2).getCell(1).setBlank();
            for (int index = 1; index <= 2; index++) sheet.getRow(index).createCell(8).setCellValue("000");
            book.write(output);
            var batch = service.preview(2L, new MockMultipartFile("file", "synthetic.xlsx", null, output.toByteArray()));
            assertEquals(List.of("ERROR", "ERROR"), batch.lines().subList(0, 2).stream().map(YouzanImportService.ResultLine::action).toList());
            confirm(2, UUID.fromString(batch.batchId()));
            assertEquals(7, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        }
    }

    @Test void supplementRequiresPresaleConfigurationAndBindsExactlyOnce() throws Exception {
        var spus = mock(ProductSpuService.class);
        var skus = mock(ProductSkuService.class);
        service = new YouzanImportService(db, new ObjectMapper(), spus, skus, Base64.getEncoder().encodeToString(new byte[32]));
        UUID id;
        try (var book = new XSSFWorkbook(file("Sheet1", false).getInputStream()); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(9).getCell(2).setBlank();
            sheet.getRow(9).getCell(14).setBlank();
            sheet.getRow(9).getCell(16).setBlank();
            sheet.getRow(1).getCell(14).setCellValue("bad");
            sheet.getRow(2).getCell(16).setCellValue("-1");
            book.write(output);
            var batch = service.preview(2L, new MockMultipartFile("file", "synthetic.xlsx", null, output.toByteArray()));
            id = UUID.fromString(batch.batchId());
            assertEquals("ERROR", batch.lines().get(0).action());
            assertEquals("ERROR", batch.lines().get(1).action());
            assertEquals("PENDING", batch.lines().get(8).action());
            assertEquals("", batch.lines().get(8).row().item().name());
            assertEquals(confirm(2, id), service.result(2L, id));
            assertEquals(7, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft", Integer.class));
        }
        var sku = new ProductSkuSaveReqVO().setPrice(999).setStock(999);
        var form = new ProductSpuSaveReqVO().setName("合成茶").setMerchantId(2L).setCategoryId(3L).setBrandId(4L)
                .setPicUrl("/synthetic/cover.png").setDeliveryTypes(List.of(1)).setDeliveryTemplateId(10L).setIsPreSale(true).setSpecType(false)
                .setSkus(List.of(sku)).setSalesCount(999).setVirtualSalesCount(999).setBrowseCount(999);
        Map<String,String> mapping = Map.of("", "008");
        assertThrows(ServiceException.class, () -> tx.execute(status -> service.supplement(2, id, "004", mapping, form)));
        verifyNoInteractions(spus, skus);
        form.setDeliveryDays(3);
        form.setName("   ");
        assertThrows(ServiceException.class, () -> tx.execute(status -> service.supplement(2, id, "004", mapping, form)));
        form.setName("补齐的合成商品");
        sku.setPrice(null).setStock(null);
        assertThrows(ServiceException.class, () -> tx.execute(status -> service.supplement(2, id, "004", mapping, form)));
        verifyNoInteractions(spus, skus);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM product_yz_import_draft WHERE product_key='004' AND name='' AND source_price IS NULL AND source_stock IS NULL AND mall_sku_id IS NULL", Integer.class));
        sku.setPrice(1008).setStock(8);
        when(spus.createUnlistedSpu(form)).thenReturn(201L);
        when(skus.getSkuListBySpuId(201L)).thenReturn(List.of(new ProductSkuDO().setId(202L)));
        assertEquals(201L, tx.execute(status -> service.supplement(2, id, "004", mapping, form)).longValue());
        assertEquals(1008, sku.getPrice());
        assertEquals(8, sku.getStock());
        assertEquals(0, form.getSalesCount());
        assertEquals(0, form.getBrowseCount());
        assertEquals(0, form.getVirtualSalesCount());
        assertEquals(202L, db.queryForObject("SELECT mall_sku_id FROM product_yz_import_draft WHERE sku_key='008'", Long.class));
        assertThrows(ServiceException.class, () -> tx.execute(status -> service.supplement(2, id, "004", mapping, form)));
        verify(spus, times(1)).createUnlistedSpu(form);
        verify(spus, never()).createSpu(any());
    }
}
