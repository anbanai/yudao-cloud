package cn.iocoder.yudao.module.infra.enums;

import lombok.Getter;

import java.util.Locale;
import java.util.Set;

/**
 * 文件上传业务场景及其固定限制。
 */
@Getter
public enum FileUploadSceneEnum {

    GENERAL("general", 0, Set.of()),
    MATERIAL("material", 10 * 1024 * 1024L, Set.of("gif", "jpg", "jpeg", "png", "bmp")),
    PRODUCT("product", 3 * 1024 * 1024L, Set.of("jpg", "jpeg", "png", "bmp")),
    MERCHANT_QUALIFICATION("merchant-qualification", 10 * 1024 * 1024L,
            Set.of("pdf", "jpg", "jpeg", "png"));

    private final String value;
    private final long maxBytes;
    private final Set<String> extensions;

    FileUploadSceneEnum(String value, long maxBytes, Set<String> extensions) {
        this.value = value;
        this.maxBytes = maxBytes;
        this.extensions = extensions;
    }

    public static FileUploadSceneEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FileUploadSceneEnum scene : values()) {
            if (scene.value.equals(normalized)) {
                return scene;
            }
        }
        throw new IllegalArgumentException("不支持的文件上传场景");
    }

}
