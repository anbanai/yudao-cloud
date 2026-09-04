package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileConfigMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import com.google.common.cache.CacheLoader;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_MASTER_VISIBILITY;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_PRIVATE_FILE_REFERENCED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_STORAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link FileConfigServiceImpl} 的单元测试类
 *
 * @author 芋道源码
 */
@Import(FileConfigServiceImpl.class)
public class FileConfigServiceImplTest extends BaseDbUnitTest {

    @Resource
    private FileConfigServiceImpl fileConfigService;

    @Resource
    private FileConfigMapper fileConfigMapper;
    @Resource
    private FileMapper fileMapper;

    @MockitoBean
    private Validator validator;
    @MockitoBean
    private FileClientFactory fileClientFactory;

    @Test
    public void testCreateFileConfig_success() {
        // 准备参数
        Map<String, Object> config = MapUtil.<String, Object>builder().put("basePath", "/yunai")
                .put("domain", "https://www.iocoder.cn").build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setStorage(FileStorageEnum.LOCAL.getStorage()).setConfig(config))
                .setId(null); // 避免 id 被赋值

        // 调用
        Long fileConfigId = fileConfigService.createFileConfig(reqVO);
        // 断言
        assertNotNull(fileConfigId);
        // 校验记录的属性是否正确
        FileConfigDO fileConfig = fileConfigMapper.selectById(fileConfigId);
        assertPojoEquals(reqVO, fileConfig, "id", "config");
        assertFalse(fileConfig.getMaster());
        assertFalse(fileConfig.getPrivateStorage());
        assertEquals("/yunai", ((LocalFileClientConfig) fileConfig.getConfig()).getBasePath());
        assertEquals("https://www.iocoder.cn", ((LocalFileClientConfig) fileConfig.getConfig()).getDomain());
        // 验证 cache
        assertNull(fileConfigService.getClientCache().getIfPresent(fileConfigId));
    }

    @Test
    public void testUpdateFileConfig_success() {
        // mock 数据
        FileConfigDO dbFileConfig = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.LOCAL.getStorage())
                .setConfig(new LocalFileClientConfig().setBasePath("/yunai").setDomain("https://www.iocoder.cn"))
                .setMaster(false).setPrivateStorage(false));
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        // 准备参数
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class, o -> {
            o.setId(dbFileConfig.getId()); // 设置更新的 ID
            o.setStorage(FileStorageEnum.LOCAL.getStorage());
            Map<String, Object> config = MapUtil.<String, Object>builder().put("basePath", "/yunai2")
                    .put("domain", "https://doc.iocoder.cn").build();
            o.setConfig(config);
        });

        // 调用
        fileConfigService.updateFileConfig(reqVO);
        // 校验是否更新正确
        FileConfigDO fileConfig = fileConfigMapper.selectById(reqVO.getId()); // 获取最新的
        assertPojoEquals(reqVO, fileConfig, "config");
        assertEquals("/yunai2", ((LocalFileClientConfig) fileConfig.getConfig()).getBasePath());
        assertEquals("https://doc.iocoder.cn", ((LocalFileClientConfig) fileConfig.getConfig()).getDomain());
        // 验证 cache
        assertNull(fileConfigService.getClientCache().getIfPresent(fileConfig.getId()));
    }

    @Test
    public void testUpdateFileConfig_notExists() {
        // 准备参数
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class);

        // 调用, 并断言异常
        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testUpdateFileConfig_rejectsChangingStorageType() {
        S3FileClientConfig oldConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        FileConfigDO fileConfig = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(oldConfig).setMaster(false).setPrivateStorage(true));
        fileConfigMapper.insert(fileConfig);
        Map<String, Object> unchangedS3Config = MapUtil.<String, Object>builder()
                .put("endpoint", "https://s3.example.com")
                .put("domain", "https://files.example.com")
                .put("bucket", "private-files")
                .put("accessKey", "access-key")
                .put("accessSecret", "access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", false)
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setId(fileConfig.getId()).setStorage(FileStorageEnum.LOCAL.getStorage())
                        .setConfig(unchangedS3Config));

        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO), FILE_CONFIG_UPDATE_FAIL_STORAGE);
    }

    @Test
    public void testUpdateFileConfigMaster_success() {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        FileConfigDO masterFileConfig = randomFileConfigDO().setMaster(true).setPrivateStorage(false);
        fileConfigMapper.insert(masterFileConfig);// @Sql: 先插入出一条存在的数据

        // 调用
        fileConfigService.updateFileConfigMaster(dbFileConfig.getId());
        // 断言数据
        assertTrue(fileConfigMapper.selectById(dbFileConfig.getId()).getMaster());
        assertFalse(fileConfigMapper.selectById(masterFileConfig.getId()).getMaster());
        // 验证 cache
        assertNull(fileConfigService.getClientCache().getIfPresent(0L));
    }

    @Test
    public void testCreateFileConfig_privateDerivedFromS3Configuration() {
        Map<String, Object> config = MapUtil.<String, Object>builder()
                .put("endpoint", "https://s3.example.com")
                .put("domain", "https://files.example.com")
                .put("bucket", "private-files")
                .put("accessKey", "access-key")
                .put("accessSecret", "access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", false)
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setStorage(FileStorageEnum.S3.getStorage()).setConfig(config)).setId(null);

        Long id = fileConfigService.createFileConfig(reqVO);

        assertTrue(fileConfigMapper.selectById(id).getPrivateStorage());
    }

    @Test
    public void testUpdateFileConfigMaster_privateDoesNotReplacePublicMaster() {
        FileConfigDO publicMaster = randomFileConfigDO().setMaster(true).setPrivateStorage(false);
        fileConfigMapper.insert(publicMaster);
        FileConfigDO oldPrivateMaster = randomFileConfigDO().setMaster(true).setPrivateStorage(true);
        fileConfigMapper.insert(oldPrivateMaster);
        S3FileClientConfig clientConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        FileConfigDO newPrivateMaster = randomPojo(FileConfigDO.class,
                o -> o.setStorage(FileStorageEnum.S3.getStorage()).setConfig(clientConfig)
                        .setMaster(false).setPrivateStorage(true));
        fileConfigMapper.insert(newPrivateMaster);

        fileConfigService.updateFileConfigMaster(newPrivateMaster.getId());

        assertTrue(fileConfigMapper.selectById(publicMaster.getId()).getMaster());
        assertFalse(fileConfigMapper.selectById(oldPrivateMaster.getId()).getMaster());
        assertTrue(fileConfigMapper.selectById(newPrivateMaster.getId()).getMaster());
    }

    @Test
    public void testUpdateFileConfigMaster_rejectsInsecurePrivateStorage() {
        S3FileClientConfig clientConfig = new S3FileClientConfig().setEndpoint("http://s3.example.com")
                .setDomain("http://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        FileConfigDO privateConfig = randomPojo(FileConfigDO.class,
                o -> o.setStorage(FileStorageEnum.S3.getStorage()).setConfig(clientConfig)
                        .setMaster(false).setPrivateStorage(true));
        fileConfigMapper.insert(privateConfig);

        assertServiceException(() -> fileConfigService.updateFileConfigMaster(privateConfig.getId()),
                FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
    }

    @Test
    public void testUpdateFileConfigMaster_rejectsLatestInsecureConfigDespiteStaleClient() {
        FileConfigDO privateConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(true);
        fileConfigMapper.insert(privateConfig);
        FileClient staleClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(privateConfig.getId())).thenReturn(staleClient);
        when(staleClient.isPrivatePresignedGetSupported()).thenReturn(true);
        assertSame(staleClient, fileConfigService.getFileClient(privateConfig.getId()));
        S3FileClientConfig insecureConfig = new S3FileClientConfig().setEndpoint("http://s3.example.com")
                .setDomain("http://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        fileConfigMapper.updateById(new FileConfigDO().setId(privateConfig.getId())
                .setStorage(FileStorageEnum.S3.getStorage()).setConfig(insecureConfig));

        assertServiceException(() -> fileConfigService.updateFileConfigMaster(privateConfig.getId()),
                FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
    }

    @Test
    public void testUpdateFileConfig_rejectsChangingMasterVisibility() {
        S3FileClientConfig oldConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        FileConfigDO master = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(oldConfig).setMaster(true).setPrivateStorage(true));
        fileConfigMapper.insert(master);
        Map<String, Object> publicConfig = MapUtil.<String, Object>builder()
                .put("endpoint", "https://s3.example.com")
                .put("domain", "https://files.example.com")
                .put("bucket", "public-files")
                .put("accessKey", "access-key")
                .put("accessSecret", "access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", true)
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setId(master.getId()).setStorage(FileStorageEnum.S3.getStorage()).setConfig(publicConfig));

        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO),
                FILE_CONFIG_UPDATE_FAIL_MASTER_VISIBILITY);
    }

    @Test
    public void testUpdateFileConfig_rejectsInsecurePrivateMaster() {
        S3FileClientConfig oldConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("access-key").setAccessSecret("access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false);
        FileConfigDO master = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(oldConfig).setMaster(true).setPrivateStorage(true));
        fileConfigMapper.insert(master);
        Map<String, Object> insecureConfig = MapUtil.<String, Object>builder()
                .put("endpoint", "http://s3.example.com")
                .put("domain", "http://files.example.com")
                .put("bucket", "private-files")
                .put("accessKey", "access-key")
                .put("accessSecret", "access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", false)
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setId(master.getId()).setStorage(FileStorageEnum.S3.getStorage()).setConfig(insecureConfig));

        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO),
                FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
    }

    @Test
    public void testUpdateFileConfig_rejectsChangingReferencedPrivateStorageLocation() {
        S3FileClientConfig oldConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("old-access-key").setAccessSecret("old-access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false).setRegion("us-east-1");
        FileConfigDO fileConfig = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(oldConfig).setMaster(false).setPrivateStorage(true));
        fileConfigMapper.insert(fileConfig);
        fileMapper.insert(randomPojo(FileDO.class, o -> o.setConfigId(fileConfig.getId())));
        Map<String, Object> changedLocation = MapUtil.<String, Object>builder()
                .put("endpoint", "https://s3.example.com")
                .put("domain", "https://files.example.com")
                .put("bucket", "another-private-bucket")
                .put("accessKey", "new-access-key")
                .put("accessSecret", "new-access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", false)
                .put("region", "us-east-1")
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setId(fileConfig.getId()).setStorage(FileStorageEnum.S3.getStorage())
                        .setConfig(changedLocation));

        assertServiceException(() -> fileConfigService.updateFileConfig(reqVO),
                FILE_CONFIG_UPDATE_FAIL_PRIVATE_FILE_REFERENCED);
    }

    @Test
    public void testUpdateFileConfig_allowsRotatingReferencedPrivateCredentials() {
        S3FileClientConfig oldConfig = new S3FileClientConfig().setEndpoint("https://s3.example.com")
                .setDomain("https://files.example.com").setBucket("private-files")
                .setAccessKey("old-access-key").setAccessSecret("old-access-secret")
                .setEnablePathStyleAccess(false).setEnablePublicAccess(false).setRegion("us-east-1");
        FileConfigDO fileConfig = randomPojo(FileConfigDO.class, o -> o.setStorage(FileStorageEnum.S3.getStorage())
                .setConfig(oldConfig).setMaster(false).setPrivateStorage(true));
        fileConfigMapper.insert(fileConfig);
        fileMapper.insert(randomPojo(FileDO.class, o -> o.setConfigId(fileConfig.getId())));
        Map<String, Object> rotatedCredentials = MapUtil.<String, Object>builder()
                .put("endpoint", "https://s3.example.com")
                .put("domain", "https://files.example.com")
                .put("bucket", "private-files")
                .put("accessKey", "new-access-key")
                .put("accessSecret", "new-access-secret")
                .put("enablePathStyleAccess", false)
                .put("enablePublicAccess", false)
                .put("region", "us-east-1")
                .build();
        FileConfigSaveReqVO reqVO = randomPojo(FileConfigSaveReqVO.class,
                o -> o.setId(fileConfig.getId()).setStorage(FileStorageEnum.S3.getStorage())
                        .setConfig(rotatedCredentials));

        fileConfigService.updateFileConfig(reqVO);

        S3FileClientConfig updated = (S3FileClientConfig) fileConfigMapper.selectById(fileConfig.getId()).getConfig();
        assertEquals("new-access-key", updated.getAccessKey());
        assertEquals("new-access-secret", updated.getAccessSecret());
    }

    @Test
    public void testGetPrivateMasterFileClient() {
        FileConfigDO publicMaster = randomFileConfigDO().setMaster(true).setPrivateStorage(false);
        fileConfigMapper.insert(publicMaster);
        FileConfigDO privateMaster = randomFileConfigDO().setMaster(true).setPrivateStorage(true);
        fileConfigMapper.insert(privateMaster);
        FileClient privateClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(privateMaster.getId())).thenReturn(privateClient);

        assertSame(privateClient, fileConfigService.getPrivateMasterFileClient());
    }

    @Test
    public void testUpdateFileConfigMaster_notExists() {
        // 调用, 并断言异常
        assertServiceException(() -> fileConfigService.updateFileConfigMaster(randomLongId()), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testDeleteFileConfig_success() {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbFileConfig.getId();

        // 调用
        fileConfigService.deleteFileConfig(id);
        // 校验数据不存在了
        assertNull(fileConfigMapper.selectById(id));
        // 验证 cache
        assertNull(fileConfigService.getClientCache().getIfPresent(id));
    }

    @Test
    public void testDeleteFileConfig_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> fileConfigService.deleteFileConfig(id), FILE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testDeleteFileConfig_master() {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(true);
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbFileConfig.getId();

        // 调用, 并断言异常
        assertServiceException(() -> fileConfigService.deleteFileConfig(id), FILE_CONFIG_DELETE_FAIL_MASTER);
    }

    @Test
    public void testDeleteFileConfig_privateFileExists() {
        FileConfigDO privateConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(true);
        fileConfigMapper.insert(privateConfig);
        fileMapper.insert(randomPojo(FileDO.class, o -> o.setConfigId(privateConfig.getId())));

        assertServiceException(() -> fileConfigService.deleteFileConfig(privateConfig.getId()),
                FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS);
        assertNotNull(fileConfigMapper.selectById(privateConfig.getId()));
    }

    @Test
    public void testDeleteFileConfigList_privateFileExists() {
        FileConfigDO publicConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(false);
        fileConfigMapper.insert(publicConfig);
        FileConfigDO privateConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(true);
        fileConfigMapper.insert(privateConfig);
        fileMapper.insert(randomPojo(FileDO.class, o -> o.setConfigId(privateConfig.getId())));

        assertServiceException(() -> fileConfigService.deleteFileConfigList(
                List.of(publicConfig.getId(), privateConfig.getId())), FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS);
        assertNotNull(fileConfigMapper.selectById(publicConfig.getId()));
        assertNotNull(fileConfigMapper.selectById(privateConfig.getId()));
    }

    @Test
    public void testGetFileConfigPage() {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setName("芋道源码")
                .setStorage(FileStorageEnum.LOCAL.getStorage());
        dbFileConfig.setCreateTime(LocalDateTimeUtil.parse("2020-01-23", DatePattern.NORM_DATE_PATTERN));// 等会查询到
        fileConfigMapper.insert(dbFileConfig);
        // 测试 name 不匹配
        fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setName("源码")));
        // 测试 storage 不匹配
        fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setStorage(FileStorageEnum.DB.getStorage())));
        // 测试 createTime 不匹配
        fileConfigMapper.insert(cloneIgnoreId(dbFileConfig, o -> o.setCreateTime(LocalDateTimeUtil.parse("2020-11-23", DatePattern.NORM_DATE_PATTERN))));
        // 准备参数
        FileConfigPageReqVO reqVO = new FileConfigPageReqVO();
        reqVO.setName("芋道");
        reqVO.setStorage(FileStorageEnum.LOCAL.getStorage());
        reqVO.setCreateTime((new LocalDateTime[]{buildTime(2020, 1, 1),
                buildTime(2020, 1, 24)}));

        // 调用
        PageResult<FileConfigDO> pageResult = fileConfigService.getFileConfigPage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbFileConfig, pageResult.getList().get(0));
    }

    @Test
    public void testFileConfig() throws Exception {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbFileConfig.getId();
        // mock 获得 Client
        FileClient fileClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(eq(id))).thenReturn(fileClient);
        when(fileClient.upload(any(), any(), any())).thenReturn("https://www.iocoder.cn");

        // 调用，并断言
        assertEquals("https://www.iocoder.cn", fileConfigService.testFileConfig(id));
    }

    @Test
    public void testFileConfig_privateReturnsShortLivedSignedUrl() throws Exception {
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false).setPrivateStorage(true);
        fileConfigMapper.insert(dbFileConfig);
        FileClient fileClient = mock(FileClient.class);
        when(fileClientFactory.getFileClient(dbFileConfig.getId())).thenReturn(fileClient);
        when(fileClient.isPrivatePresignedGetSupported()).thenReturn(true);
        when(fileClient.upload(any(), any(), any()))
                .thenReturn("https://files.example.com/test.jpg?signature=old&expires=86400");
        when(fileClient.presignGetUrl("https://files.example.com/test.jpg", 900))
                .thenReturn("https://files.example.com/test.jpg?signature=new&expires=900");

        assertEquals("https://files.example.com/test.jpg?signature=new&expires=900",
                fileConfigService.testFileConfig(dbFileConfig.getId()));
    }

    @Test
    public void testGetFileConfig() {
        // mock 数据
        FileConfigDO dbFileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(dbFileConfig);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbFileConfig.getId();

        // 调用，并断言
        assertPojoEquals(dbFileConfig, fileConfigService.getFileConfig(id));
    }

    @Test
    public void testGetFileClient() {
        // mock 数据
        FileConfigDO fileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(fileConfig);
        // 准备参数
        Long id = fileConfig.getId();
        // mock 获得 Client
        FileClient fileClient = new LocalFileClient(id, new LocalFileClientConfig());
        when(fileClientFactory.getFileClient(eq(id))).thenReturn(fileClient);

        // 调用，并断言
        assertSame(fileClient, fileConfigService.getFileClient(id));
        // 断言缓存
        verify(fileClientFactory).createOrUpdateFileClient(eq(id), eq(fileConfig.getStorage()),
                eq(fileConfig.getConfig()));
    }

    @Test
    public void testGetFileClient_retriesInitializationWhenFactoryReturnsNull() {
        FileConfigDO fileConfig = randomFileConfigDO().setMaster(false);
        fileConfigMapper.insert(fileConfig);
        FileClient fileClient = new LocalFileClient(fileConfig.getId(), new LocalFileClientConfig());
        when(fileClientFactory.getFileClient(fileConfig.getId())).thenReturn(null, fileClient);

        assertThrows(CacheLoader.InvalidCacheLoadException.class,
                () -> fileConfigService.getFileClient(fileConfig.getId()));
        assertSame(fileClient, fileConfigService.getFileClient(fileConfig.getId()));
        verify(fileClientFactory, times(2)).createOrUpdateFileClient(
                fileConfig.getId(), fileConfig.getStorage(), fileConfig.getConfig());
    }

    @Test
    public void testGetMasterFileClient() {
        // mock 数据
        FileConfigDO fileConfig = randomFileConfigDO().setMaster(true);
        fileConfigMapper.insert(fileConfig);
        // 准备参数
        Long id = fileConfig.getId();
        // mock 获得 Client
        FileClient fileClient = new LocalFileClient(id, new LocalFileClientConfig());
        when(fileClientFactory.getFileClient(eq(fileConfig.getId()))).thenReturn(fileClient);

        // 调用，并断言
        assertSame(fileClient, fileConfigService.getMasterFileClient());
        // 断言缓存
        verify(fileClientFactory).createOrUpdateFileClient(eq(fileConfig.getId()), eq(fileConfig.getStorage()),
                eq(fileConfig.getConfig()));
    }

    @Test
    public void testGetMasterFileClient_notExists() {
        assertNull(fileConfigService.getMasterFileClient());
    }

    private FileConfigDO randomFileConfigDO() {
        return randomPojo(FileConfigDO.class).setStorage(randomEle(FileStorageEnum.values()).getStorage())
                .setConfig(new EmptyFileClientConfig()).setPrivateStorage(false);
    }

    @Data
    public static class EmptyFileClientConfig implements FileClientConfig, Serializable {

    }

}
