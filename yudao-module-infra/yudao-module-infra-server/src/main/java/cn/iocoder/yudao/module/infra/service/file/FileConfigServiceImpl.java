package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.convert.file.FileConfigConvert;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileConfigMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Striped;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.cache.CacheUtils.buildAsyncReloadingCache;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_MASTER_VISIBILITY;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_PRIVATE_FILE_REFERENCED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_UPDATE_FAIL_STORAGE;

/**
 * 文件配置 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class FileConfigServiceImpl implements FileConfigService {

    private static final Long CACHE_MASTER_ID = 0L;

    private final ReentrantReadWriteLock privateConfigLifecycleLock = new ReentrantReadWriteLock(true);
    private final Striped<Lock> clientRefreshLocks = Striped.lazyWeakLock(32);
    private final ConcurrentMap<Long, String> clientConfigFingerprints = new ConcurrentHashMap<>();

    private volatile String databaseProductName;

    /**
     * {@link FileClient} 缓存，通过它异步刷新 fileClientFactory
     */
    @Getter
    private final LoadingCache<Long, FileClient> clientCache = buildAsyncReloadingCache(Duration.ofSeconds(10L),
            new CacheLoader<Long, FileClient>() {

                @Override
                public FileClient load(Long id) {
                    if (Objects.equals(CACHE_MASTER_ID, id)) {
                        return createMasterFileClient();
                    }
                    FileConfigDO config = fileConfigMapper.selectById(id);
                    return config != null ? createOrUpdateFileClient(config) : fileClientFactory.getFileClient(id);
                }

            });

    @Resource
    private FileClientFactory fileClientFactory;

    @Resource
    private FileConfigMapper fileConfigMapper;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private Validator validator;

    @Resource
    private DataSource dataSource;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public Long createFileConfig(FileConfigSaveReqVO createReqVO) {
        FileConfigDO fileConfig = FileConfigConvert.INSTANCE.convert(createReqVO)
                .setConfig(parseClientConfig(createReqVO.getStorage(), createReqVO.getConfig()))
                .setMaster(false); // 默认非 master
        fileConfig.setPrivateStorage(fileConfig.getConfig().isPrivateAccess());
        fileConfigMapper.insert(fileConfig);
        return fileConfig.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFileConfig(FileConfigSaveReqVO updateReqVO) {
        // 先读取访问类型并取得生命周期锁，保持与私有文件上传一致的加锁顺序
        FileConfigDO config = validateFileConfigExists(updateReqVO.getId());
        if (Boolean.TRUE.equals(config.getPrivateStorage())) {
            lockPrivateConfigLifecycle(privateConfigLifecycleLock.writeLock());
        }
        config = validateFileConfigExistsForUpdate(updateReqVO.getId());
        if (!Objects.equals(config.getStorage(), updateReqVO.getStorage())) {
            throw exception(FILE_CONFIG_UPDATE_FAIL_STORAGE);
        }
        // 更新
        FileClientConfig clientConfig = parseClientConfig(config.getStorage(), updateReqVO.getConfig());
        boolean privateStorage = clientConfig.isPrivateAccess();
        if (Boolean.TRUE.equals(config.getPrivateStorage()) && fileMapper.selectCountByConfigId(config.getId()) > 0
                && !config.getConfig().isSameStorageLocation(clientConfig)) {
            throw exception(FILE_CONFIG_UPDATE_FAIL_PRIVATE_FILE_REFERENCED);
        }
        if (Boolean.TRUE.equals(config.getMaster())
                && !Objects.equals(config.getPrivateStorage(), privateStorage)) {
            throw exception(FILE_CONFIG_UPDATE_FAIL_MASTER_VISIBILITY);
        }
        if (Boolean.TRUE.equals(config.getMaster()) && privateStorage
                && !clientConfig.isPrivatePresignedGetSupported()) {
            throw exception(FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
        }
        FileConfigDO updateObj = FileConfigConvert.INSTANCE.convert(updateReqVO)
                .setConfig(clientConfig).setPrivateStorage(privateStorage);
        fileConfigMapper.updateById(updateObj);

        // 清空缓存
        clearCache(config.getId(), Boolean.TRUE.equals(config.getMaster())
                && !Boolean.TRUE.equals(config.getPrivateStorage()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFileConfigMaster(Long id) {
        // 先读取候选配置的访问类型，再串行化默认配置生命周期变更
        FileConfigDO snapshot = validateFileConfigExists(id);
        lockPrivateConfigLifecycle(privateConfigLifecycleLock.writeLock());
        boolean privateStorage = Boolean.TRUE.equals(snapshot.getPrivateStorage());
        // 按 ID 升序锁定整个访问类型分组，与批量删除保持一致的锁顺序
        List<FileConfigDO> lockedConfigs = fileConfigMapper.selectByPrivateForUpdate(privateStorage);
        FileConfigDO config = lockedConfigs.stream().filter(item -> Objects.equals(item.getId(), id))
                .findFirst().orElse(null);
        if (config == null) {
            throw exception(FILE_CONFIG_NOT_EXISTS);
        }
        if (!Objects.equals(snapshot.getPrivateStorage(), config.getPrivateStorage())) {
            throw exception(FILE_CONFIG_UPDATE_FAIL_MASTER_VISIBILITY);
        }
        if (privateStorage) {
            if (!config.getConfig().isPrivatePresignedGetSupported()) {
                throw exception(FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
            }
        }
        // 更新同访问类型的其它配置为非 master
        fileConfigMapper.updateMasterByPrivate(false, privateStorage);
        // 更新
        fileConfigMapper.updateById(new FileConfigDO().setId(id).setMaster(true));

        // 清空缓存
        clearCache(null, !privateStorage);
    }

    private FileClientConfig parseClientConfig(Integer storage, Map<String, Object> config) {
        // 获取配置类
        Class<? extends FileClientConfig> configClass = FileStorageEnum.getByStorage(storage)
                .getConfigClass();
        FileClientConfig clientConfig = JsonUtils.parseObject2(JsonUtils.toJsonString(config), configClass);
        // 参数校验
        ValidationUtils.validate(validator, clientConfig);
        // 设置参数
        return clientConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileConfig(Long id) {
        // 先读取访问类型并取得生命周期锁，保持与私有文件上传一致的加锁顺序
        FileConfigDO config = validateFileConfigExists(id);
        if (Boolean.TRUE.equals(config.getPrivateStorage())) {
            lockPrivateConfigLifecycle(privateConfigLifecycleLock.writeLock());
        }
        config = validateFileConfigExistsForUpdate(id);
        if (Boolean.TRUE.equals(config.getMaster())) {
            throw exception(FILE_CONFIG_DELETE_FAIL_MASTER);
        }
        validatePrivateFileConfigNotReferenced(config);
        // 删除
        fileConfigMapper.deleteById(id);

        // 清空缓存
        clearCache(id, config.getMaster());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileConfigList(List<Long> ids) {
        List<FileConfigDO> snapshots = fileConfigMapper.selectBatchIds(ids);
        if (snapshots.stream().anyMatch(config -> Boolean.TRUE.equals(config.getPrivateStorage()))) {
            lockPrivateConfigLifecycle(privateConfigLifecycleLock.writeLock());
        }
        // 校验是否有主配置
        List<FileConfigDO> configs = fileConfigMapper.selectByIdsForUpdate(ids);
        for (FileConfigDO config : configs) {
            if (Boolean.TRUE.equals(config.getMaster())) {
                throw exception(FILE_CONFIG_DELETE_FAIL_MASTER);
            }
            validatePrivateFileConfigNotReferenced(config);
        }

        // 批量删除
        fileConfigMapper.deleteByIds(ids);

        // 清空缓存
        ids.forEach(id -> clearCache(id, false));
    }

    private void validatePrivateFileConfigNotReferenced(FileConfigDO config) {
        if (Boolean.TRUE.equals(config.getPrivateStorage())
                && fileMapper.selectCountByConfigId(config.getId()) > 0) {
            throw exception(FILE_CONFIG_DELETE_FAIL_PRIVATE_FILE_EXISTS);
        }
    }

    /**
     * 清空指定文件配置
     *
     * @param id     配置编号
     * @param master 是否主配置
     */
    private void clearCache(Long id, Boolean master) {
        if (id != null) {
            clientCache.invalidate(id);
            clientConfigFingerprints.remove(id);
        }
        if (Boolean.TRUE.equals(master)) {
            clientCache.invalidate(CACHE_MASTER_ID);
        }
    }

    private FileConfigDO validateFileConfigExists(Long id) {
        FileConfigDO config = fileConfigMapper.selectById(id);
        if (config == null) {
            throw exception(FILE_CONFIG_NOT_EXISTS);
        }
        return config;
    }

    private FileConfigDO validateFileConfigExistsForUpdate(Long id) {
        FileConfigDO config = fileConfigMapper.selectByIdForUpdate(id);
        if (config == null) {
            throw exception(FILE_CONFIG_NOT_EXISTS);
        }
        return config;
    }

    @Override
    public FileConfigDO getFileConfig(Long id) {
        return fileConfigMapper.selectById(id);
    }

    @Override
    public PageResult<FileConfigDO> getFileConfigPage(FileConfigPageReqVO pageReqVO) {
        return fileConfigMapper.selectPage(pageReqVO);
    }

    @Override
    public String testFileConfig(Long id) throws Exception {
        // 校验存在
        FileConfigDO config = validateFileConfigExists(id);
        FileClient client = getFileClient(id);
        if (Boolean.TRUE.equals(config.getPrivateStorage()) && !client.isPrivatePresignedGetSupported()) {
            throw exception(FILE_CONFIG_PRIVATE_MASTER_UNSUPPORTED);
        }
        // 上传文件
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        String url = client.upload(content, "public" + StrUtil.SLASH + IdUtil.fastSimpleUUID() + ".jpg", "image/jpeg");
        if (!Boolean.TRUE.equals(config.getPrivateStorage())) {
            return url;
        }
        return client.presignGetUrl(HttpUtils.removeUrlQuery(url), 15 * 60);
    }

    @Override
    public FileClient getFileClient(Long id) {
        return clientCache.getUnchecked(id);
    }

    @Override
    public FileClient getMasterFileClient() {
        try {
            return clientCache.getUnchecked(CACHE_MASTER_ID);
        } catch (CacheLoader.InvalidCacheLoadException exception) {
            return null;
        }
    }

    @Override
    public FileClient getPrivateMasterFileClient() {
        FileConfigDO config = fileConfigMapper.selectByMaster(true);
        return config != null ? getFileClient(config.getId()) : null;
    }

    @Override
    public FileClient getPrivateMasterFileClientForShare() {
        lockPrivateConfigLifecycle(privateConfigLifecycleLock.readLock());
        FileConfigDO config;
        String productName = getDatabaseProductName();
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            config = fileConfigMapper.selectByMasterForShare(true);
        } else if (productName.contains("h2")) {
            // H2 不支持共享行锁；单元测试由上面的事务级读写锁覆盖并发语义
            config = fileConfigMapper.selectByMaster(true);
        } else {
            // 未适配共享锁语法的数据库保守使用排他锁，保证正确性
            config = fileConfigMapper.selectByMasterForUpdate(true);
        }
        if (config == null) {
            return null;
        }
        // 共享行锁已阻止配置更新，避免为并发上传升级为排他锁。
        FileClient client = createOrUpdateFileClientFromCurrentConfig(config);
        if (client != null) {
            clientCache.put(config.getId(), client);
        }
        return client;
    }

    private FileClient createOrUpdateFileClient(FileConfigDO config) {
        return new TransactionTemplate(transactionManager).execute(status ->
                createOrUpdateFileClientInTransaction(config));
    }

    private FileClient createMasterFileClient() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            FileConfigDO master = fileConfigMapper.selectByMasterForUpdate(false);
            return master != null ? createOrUpdateFileClientFromCurrentConfig(master) : null;
        });
    }

    private FileClient createOrUpdateFileClientInTransaction(FileConfigDO config) {
        // 异步缓存刷新可能拿到旧快照；锁定当前数据库记录直到客户端初始化完成。
        FileConfigDO latestConfig = fileConfigMapper.selectByIdForUpdate(config.getId());
        if (latestConfig == null) {
            return fileClientFactory.getFileClient(config.getId());
        }
        return createOrUpdateFileClientFromCurrentConfig(latestConfig);
    }

    private FileClient createOrUpdateFileClientFromCurrentConfig(FileConfigDO config) {
        Lock lock = clientRefreshLocks.get(config.getId());
        lock.lock();
        try {
            String fingerprint = JsonUtils.toJsonString(config.getConfig());
            if (!Objects.equals(fingerprint, clientConfigFingerprints.get(config.getId()))) {
                fileClientFactory.createOrUpdateFileClient(config.getId(), config.getStorage(), config.getConfig());
                FileClient client = fileClientFactory.getFileClient(config.getId());
                if (client != null) {
                    clientConfigFingerprints.put(config.getId(), fingerprint);
                } else {
                    clientConfigFingerprints.remove(config.getId());
                }
                return client;
            }
            return fileClientFactory.getFileClient(config.getId());
        } finally {
            lock.unlock();
        }
    }

    private void lockPrivateConfigLifecycle(Lock lock) {
        lock.lock();
        try {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("文件配置生命周期锁必须在事务中使用");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int status) {
                    lock.unlock();
                }

            });
        } catch (RuntimeException exception) {
            lock.unlock();
            throw exception;
        }
    }

    private String getDatabaseProductName() {
        String productName = databaseProductName;
        if (productName != null) {
            return productName;
        }
        synchronized (this) {
            if (databaseProductName != null) {
                return databaseProductName;
            }
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
                return databaseProductName;
            } catch (SQLException exception) {
                throw new IllegalStateException("无法识别文件配置数据库类型", exception);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }

}
