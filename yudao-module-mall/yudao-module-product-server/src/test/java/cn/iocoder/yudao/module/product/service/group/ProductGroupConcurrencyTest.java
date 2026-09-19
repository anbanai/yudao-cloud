package cn.iocoder.yudao.module.product.service.group;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuBatchReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuUpdateReqVO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.GROUP_HAVE_BIND_SPU;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.GROUP_NOT_EXISTS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Import({ProductGroupOperationsService.class, ProductGroupServiceImpl.class})
class ProductGroupConcurrencyTest extends BaseDbUnitTest {

    @Resource private ProductGroupOperationsService operations;
    @Resource private ProductGroupService groupService;
    @Resource private DataSource dataSource;
    @Resource private PlatformTransactionManager transactionManager;
    @MockBean private ProductCategoryService categoryService;

    @BeforeEach
    void prepare() {
        jdbc().update("INSERT INTO product_group (id,name,sort,status,storefront_visible,tenant_id)"
                + " VALUES (10,'concurrent group',0,0,TRUE,0)");
        jdbc().update("INSERT INTO product_spu (id,name,keyword,introduction,description,bar_code,category_id,"
                + "pic_url,unit,sort,status,spec_type,price,market_price,cost_price,stock,delivery_template_id,"
                + "recommend_hot,recommend_benefit,recommend_best,recommend_new,recommend_good,give_integral,"
                + "sub_commission_type,tenant_id) VALUES (100,'tea','','','','',2,'',0,0,1,FALSE,100,100,100,"
                + "1,1,FALSE,FALSE,FALSE,FALSE,FALSE,0,FALSE,0)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"batch", "legacyAdd", "legacySync"})
    void deletingGroupCannotCommitPastAnUncommittedMemberInsert(String writer) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commitInsert = new CountDownLatch(1);
        try {
            Future<?> insertion = executor.submit(() -> withTenant(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        insertMember(writer);
                        inserted.countDown();
                        awaitLatch(commitInsert);
                    })));
            assertTrue(inserted.await(10, TimeUnit.SECONDS), "writer must reach its uncommitted insert");
            Future<Throwable> deletion = executor.submit(() -> {
                try {
                    withTenant(() -> groupService.deleteGroup(10L));
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            // Before the fix, legacy writes allow deletion to finish immediately. The batch writer
            // blocks deletion only at UPDATE, after its stale empty read. No timing sleep is needed.
            await().atMost(Duration.ofSeconds(10)).until(() -> deletion.isDone() || hasBlockedSession());
            commitInsert.countDown();
            insertion.get(10, TimeUnit.SECONDS);
            Throwable failure = deletion.get(10, TimeUnit.SECONDS);
            ServiceException rejection = assertInstanceOf(ServiceException.class, failure);
            assertEquals(GROUP_HAVE_BIND_SPU.getCode(), rejection.getCode());
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM product_group WHERE id=10 AND deleted=FALSE", Integer.class));
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM product_group_spu WHERE group_id=10 AND spu_id=100", Integer.class));
        } finally {
            commitInsert.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"batch", "legacyAdd", "legacySync"})
    void addingMembersAfterAConcurrentDeletionRejectsTheDeletedGroup(String writer) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commitDelete = new CountDownLatch(1);
        try {
            Future<?> deletion = executor.submit(() -> withTenant(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        groupService.deleteGroup(10L);
                        deleted.countDown();
                        awaitLatch(commitDelete);
                    })));
            assertTrue(deleted.await(10, TimeUnit.SECONDS));
            Future<Throwable> insertion = executor.submit(() -> {
                try {
                    withTenant(() -> insertMember(writer));
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> insertion.isDone() || hasBlockedSession());
            commitDelete.countDown();
            deletion.get(10, TimeUnit.SECONDS);
            ServiceException rejection = assertInstanceOf(ServiceException.class, insertion.get(10, TimeUnit.SECONDS));
            assertEquals(GROUP_NOT_EXISTS.getCode(), rejection.getCode());
            assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM product_group_spu WHERE group_id=10", Integer.class));
        } finally {
            commitDelete.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacyAdd", "legacySync"})
    void legacyWritersReReadCommittedMembershipAfterWaitingForTheProduct(String writer) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commitInsert = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> withTenant(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        insertMember("batch");
                        inserted.countDown();
                        awaitLatch(commitInsert);
                    })));
            assertTrue(inserted.await(10, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> withTenant(() -> insertMember(writer)));
            await().atMost(Duration.ofSeconds(10)).until(() -> second.isDone() || hasBlockedSession());
            commitInsert.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM product_group_spu WHERE group_id=10 AND spu_id=100", Integer.class));
        } finally {
            commitInsert.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void insertMember(String writer) {
        switch (writer) {
            case "batch" -> operations.updateSpuGroups(new ProductGroupSpuUpdateReqVO()
                    .setSpuIds(List.of(100L)).setGroupIds(List.of(10L)).setOperation("add"));
            case "legacyAdd" -> groupService.addSpus(new ProductGroupSpuBatchReqVO()
                    .setGroupId(10L).setSpuIds(List.of(100L)));
            case "legacySync" -> groupService.syncSpuGroups(100L, List.of(10L));
            default -> throw new IllegalArgumentException(writer);
        }
    }

    private boolean hasBlockedSession() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",
                Integer.class) > 0;
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static void withTenant(Runnable action) {
        TenantContextHolder.setTenantId(0L);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(15, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
