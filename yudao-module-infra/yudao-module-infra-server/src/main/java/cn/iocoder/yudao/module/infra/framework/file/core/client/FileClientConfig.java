package cn.iocoder.yudao.module.infra.framework.file.core.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

/**
 * 文件客户端的配置
 * 不同实现的客户端，需要不同的配置，通过子类来定义
 *
 * @author 芋道源码
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
// @JsonTypeInfo 注解的作用，Jackson 多态
// 1. 序列化到时数据库时，增加 @class 属性。
// 2. 反序列化到内存对象时，通过 @class 属性，可以创建出正确的类型
public interface FileClientConfig {

    /**
     * 是否为私有访问存储。
     */
    @JsonIgnore
    default boolean isPrivateAccess() {
        return false;
    }

    /**
     * 是否能生成 HTTPS、有限时效且底层对象不可公开读取的下载地址。
     */
    @JsonIgnore
    default boolean isPrivatePresignedGetSupported() {
        return false;
    }

    /**
     * 判断另一配置是否仍指向同一个存储位置。
     *
     * 私有历史文件依赖原配置生成签名，因此存储位置不可变；访问凭证可由具体客户端排除在比较之外。
     */
    @JsonIgnore
    default boolean isSameStorageLocation(FileClientConfig other) {
        return Objects.equals(this, other);
    }

}
