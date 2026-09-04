package cn.iocoder.yudao.module.infra.dal.mysql.file;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileConfigMapperTest {

    @Test
    void sharedLockQuery_skipsIncompatibleSqlParserInterceptors() {
        IgnoreStrategy strategy = InterceptorIgnoreHelper.initSqlParserInfoCache(FileConfigMapper.class);

        assertNotNull(strategy);
        assertTrue(Boolean.TRUE.equals(strategy.getTenantLine()));
        assertTrue(Boolean.TRUE.equals(strategy.getDataPermission()));
        String mappedStatementId = FileConfigMapper.class.getName() + ".selectOne";
        assertTrue(InterceptorIgnoreHelper.willIgnoreTenantLine(mappedStatementId));
        assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission(mappedStatementId));
    }

}
