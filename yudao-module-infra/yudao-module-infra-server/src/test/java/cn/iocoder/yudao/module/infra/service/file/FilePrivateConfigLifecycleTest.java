package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileConfigMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import({FileConfigServiceImpl.class, FileServiceImpl.class})
class FilePrivateConfigLifecycleTest extends BaseDbUnitTest {

    @Resource
    private FileConfigServiceImpl fileConfigService;
    @Resource
    private FileServiceImpl fileService;
    @Resource
    private FileConfigMapper fileConfigMapper;

    @MockitoBean
    private Validator validator;
    @MockitoBean
    private FileClientFactory fileClientFactory;

    @Test
    void testCreatePrivateFile_blocksConcurrentMasterSwitchAndDelete() throws Exception {
        FileConfigDO currentMaster = createPrivateConfig("current", true);
        FileConfigDO nextMaster = createPrivateConfig("next", false);
        CountDownLatch uploadStarted = new CountDownLatch(1);
        CountDownLatch allowUpload = new CountDownLatch(1);
        FileClient currentClient = mock(FileClient.class);
        when(currentClient.getId()).thenReturn(currentMaster.getId());
        when(currentClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(currentClient.upload(eq(new byte[]{1}), anyString(), eq("image/png"))).thenAnswer(invocation -> {
            uploadStarted.countDown();
            assertTrue(allowUpload.await(5, TimeUnit.SECONDS));
            return "https://files.example.com/label.png";
        });
        FileClient nextClient = mock(FileClient.class);
        when(nextClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(fileClientFactory.getFileClient(currentMaster.getId())).thenReturn(currentClient);
        when(fileClientFactory.getFileClient(nextMaster.getId())).thenReturn(nextClient);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FileDO> uploadFuture = executor.submit(() -> fileService.createPrivateFile(
                    new byte[]{1}, "label.png", "trade/logistics/1/labels", "image/png"));
            assertTrue(uploadStarted.await(5, TimeUnit.SECONDS));
            Future<?> lifecycleFuture = executor.submit(() -> {
                fileConfigService.updateFileConfigMaster(nextMaster.getId());
                fileConfigService.deleteFileConfig(currentMaster.getId());
            });
            try {
                assertThrows(TimeoutException.class, () -> lifecycleFuture.get(300, TimeUnit.MILLISECONDS));
            } finally {
                allowUpload.countDown();
            }

            assertNotNull(uploadFuture.get(5, TimeUnit.SECONDS).getId());
            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> lifecycleFuture.get(5, TimeUnit.SECONDS));
            ServiceException cause = (ServiceException) exception.getCause();
            assertEquals(FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS.getCode(), cause.getCode());
            assertNotNull(fileConfigMapper.selectById(currentMaster.getId()));
        } finally {
            allowUpload.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void testCreatePrivateFile_allowsConcurrentUploads() throws Exception {
        FileConfigDO currentMaster = createPrivateConfig("current", true);
        CountDownLatch bothUploadsStarted = new CountDownLatch(2);
        CountDownLatch allowUploads = new CountDownLatch(1);
        FileClient currentClient = mock(FileClient.class);
        when(currentClient.getId()).thenReturn(currentMaster.getId());
        when(currentClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(currentClient.upload(eq(new byte[]{1}), anyString(), eq("image/png"))).thenAnswer(invocation -> {
            bothUploadsStarted.countDown();
            assertTrue(allowUploads.await(5, TimeUnit.SECONDS));
            return "https://files.example.com/" + invocation.getArgument(1);
        });
        when(fileClientFactory.getFileClient(currentMaster.getId())).thenReturn(currentClient);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FileDO> first = executor.submit(() -> fileService.createPrivateFile(
                    new byte[]{1}, "first.png", "trade/logistics/1/labels", "image/png"));
            Future<FileDO> second = executor.submit(() -> fileService.createPrivateFile(
                    new byte[]{1}, "second.png", "trade/logistics/1/labels", "image/png"));
            try {
                assertTrue(bothUploadsStarted.await(1, TimeUnit.SECONDS));
            } finally {
                allowUploads.countDown();
            }
            assertNotNull(first.get(5, TimeUnit.SECONDS).getId());
            assertNotNull(second.get(5, TimeUnit.SECONDS).getId());
        } finally {
            allowUploads.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void testUpdateFileConfigMaster_preservesDefaultDuringConcurrentCandidateDelete() throws Exception {
        FileConfigDO currentMaster = createPrivateConfig("current", true);
        FileConfigDO candidate = createPrivateConfig("candidate", false);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> switchFuture = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                fileConfigService.updateFileConfigMaster(candidate.getId());
                return null;
            });
            Future<?> deleteFuture = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                fileConfigService.deleteFileConfig(candidate.getId());
                return null;
            });
            start.countDown();

            Throwable switchFailure = getFailure(switchFuture);
            Throwable deleteFailure = getFailure(deleteFuture);
            FileConfigDO persistedCandidate = fileConfigMapper.selectById(candidate.getId());
            FileConfigDO persistedCurrent = fileConfigMapper.selectById(currentMaster.getId());
            if (persistedCandidate != null) {
                assertTrue(persistedCandidate.getMaster());
                assertFalse(persistedCurrent.getMaster());
                assertEquals(FILE_CONFIG_DELETE_FAIL_MASTER.getCode(), ((ServiceException) deleteFailure).getCode());
                assertNull(switchFailure);
            } else {
                assertTrue(persistedCurrent.getMaster());
                assertNotNull(switchFailure);
                assertNull(deleteFailure);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static Throwable getFailure(Future<?> future) throws Exception {
        try {
            future.get(5, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    @Test
    void testCreatePrivateFile_refreshesClientFromLockedDatabaseConfig() throws Exception {
        FileConfigDO currentMaster = createPrivateConfig("old-bucket", true);
        FileClient staleClient = mock(FileClient.class);
        when(staleClient.getId()).thenReturn(currentMaster.getId());
        when(staleClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(staleClient.upload(eq(new byte[]{1}), anyString(), eq("image/png")))
                .thenReturn("https://files.example.com/stale.png");
        FileClient refreshedClient = mock(FileClient.class);
        when(refreshedClient.getId()).thenReturn(currentMaster.getId());
        when(refreshedClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(refreshedClient.upload(eq(new byte[]{1}), anyString(), eq("image/png")))
                .thenReturn("https://files.example.com/refreshed.png");
        when(fileClientFactory.getFileClient(currentMaster.getId()))
                .thenReturn(staleClient, refreshedClient);
        assertEquals(staleClient, fileConfigService.getPrivateMasterFileClient());

        S3FileClientConfig updatedConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("new-bucket")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false).setRegion("us-east-1");
        fileConfigMapper.updateById(new FileConfigDO().setId(currentMaster.getId()).setConfig(updatedConfig));

        FileDO file = fileService.createPrivateFile(
                new byte[]{1}, "label.png", "trade/logistics/1/labels", "image/png");

        assertEquals("https://files.example.com/refreshed.png", file.getUrl());
        verify(refreshedClient).upload(eq(new byte[]{1}), anyString(), eq("image/png"));
        verify(staleClient, never()).upload(eq(new byte[]{1}), anyString(), eq("image/png"));
    }

    @Test
    void testPrivateMasterSupported_refreshesClientFromLockedDatabaseConfig() {
        FileConfigDO currentMaster = createPrivateConfig("old-bucket", true);
        FileClient staleClient = mock(FileClient.class);
        when(staleClient.getId()).thenReturn(currentMaster.getId());
        when(staleClient.isPrivatePresignedGetSupported()).thenReturn(false);
        FileClient refreshedClient = mock(FileClient.class);
        when(refreshedClient.getId()).thenReturn(currentMaster.getId());
        when(refreshedClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(fileClientFactory.getFileClient(currentMaster.getId()))
                .thenReturn(staleClient, refreshedClient);
        assertSame(staleClient, fileConfigService.getPrivateMasterFileClient());

        S3FileClientConfig updatedConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("new-bucket")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false).setRegion("us-east-1");
        fileConfigMapper.updateById(new FileConfigDO().setId(currentMaster.getId()).setConfig(updatedConfig));

        assertTrue(fileService.isPrivateMasterSupported());
        verify(fileClientFactory, times(2)).createOrUpdateFileClient(eq(currentMaster.getId()),
                eq(currentMaster.getStorage()), any());
    }

    private FileConfigDO createPrivateConfig(String name, boolean master) {
        S3FileClientConfig clientConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket(name)
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false).setRegion("us-east-1");
        FileConfigDO config = new FileConfigDO().setName(name).setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(clientConfig).setMaster(master).setPrivateStorage(true);
        fileConfigMapper.insert(config);
        return config;
    }

}
