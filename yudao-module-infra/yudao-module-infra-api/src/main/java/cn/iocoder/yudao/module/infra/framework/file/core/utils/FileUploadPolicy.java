package cn.iocoder.yudao.module.infra.framework.file.core.utils;

import cn.hutool.core.io.FileUtil;
import cn.iocoder.yudao.module.infra.enums.FileUploadSceneEnum;

import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_SIZE_EXCEEDED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TYPE_NOT_SUPPORTED;

/**
 * 文件上传策略校验。
 */
public final class FileUploadPolicy {

    private FileUploadPolicy() {
    }

    public static void validateImage(FileUploadSceneEnum scene, String name, byte[] content, String declaredType) {
        if (scene == null || scene == FileUploadSceneEnum.GENERAL) {
            return;
        }
        validateSize(scene, content == null ? 0 : content.length);
        String extension = normalizeExtension(name);
        String actualType = detectType(content);
        if (!scene.getExtensions().contains(extension) || !isAllowedImageType(scene, actualType)
                || (declaredType != null && !declaredType.isBlank() && !isSameImageType(declaredType, actualType))) {
            throw exception(FILE_UPLOAD_TYPE_NOT_SUPPORTED, scene.getValue(), scene.getExtensions());
        }
    }

    public static void validateMetadata(FileUploadSceneEnum scene, String name, String type, Long size) {
        if (scene == null || scene == FileUploadSceneEnum.GENERAL) {
            return;
        }
        validateSize(scene, size == null ? 0 : size);
        String extension = normalizeExtension(name);
        if (!scene.getExtensions().contains(extension) || !isAllowedType(scene, type)) {
            throw exception(FILE_UPLOAD_TYPE_NOT_SUPPORTED, scene.getValue(), scene.getExtensions());
        }
    }

    public static void validateQualificationDocument(String name, byte[] content, String declaredType) {
        FileUploadSceneEnum scene = FileUploadSceneEnum.MERCHANT_QUALIFICATION;
        validateSize(scene, content == null ? 0 : content.length);
        String extension = normalizeExtension(name);
        String actualType = detectDocumentType(content);
        if (!scene.getExtensions().contains(extension) || !isQualificationType(extension, actualType)
                || (declaredType != null && !declaredType.isBlank()
                && !declaredType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals(actualType))) {
            throw exception(FILE_UPLOAD_TYPE_NOT_SUPPORTED, scene.getValue(), scene.getExtensions());
        }
    }

    private static void validateSize(FileUploadSceneEnum scene, long size) {
        if (size > scene.getMaxBytes()) {
            throw exception(FILE_UPLOAD_SIZE_EXCEEDED, scene.getValue(), scene.getMaxBytes() / 1024 / 1024);
        }
    }

    private static String normalizeExtension(String name) {
        return FileUtil.extName(name == null ? "" : name).toLowerCase(Locale.ROOT);
    }

    private static String detectType(byte[] content) {
        if (content != null && content.length >= 3 && content[0] == 'G' && content[1] == 'I' && content[2] == 'F') {
            return "image/gif";
        }
        if (content != null && content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 'P'
                && content[2] == 'N' && content[3] == 'G') {
            return "image/png";
        }
        if (content != null && content.length >= 2 && content[0] == (byte) 0xFF && content[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (content != null && content.length >= 2 && content[0] == 'B' && content[1] == 'M') {
            return "image/bmp";
        }
        return "";
    }

    private static String detectDocumentType(byte[] content) {
        if (content != null && content.length >= 5 && content[0] == '%'
                && content[1] == 'P' && content[2] == 'D' && content[3] == 'F' && content[4] == '-') {
            return "application/pdf";
        }
        return detectType(content);
    }

    private static boolean isQualificationType(String extension, String type) {
        return switch (extension) {
            case "pdf" -> "application/pdf".equals(type);
            case "jpg", "jpeg" -> "image/jpeg".equals(type);
            case "png" -> "image/png".equals(type);
            default -> false;
        };
    }

    private static boolean isAllowedType(FileUploadSceneEnum scene, String type) {
        if (scene == FileUploadSceneEnum.MERCHANT_QUALIFICATION) {
            return "application/pdf".equals(type) || "image/jpeg".equals(type) || "image/png".equals(type);
        }
        return isAllowedImageType(scene, type);
    }

    private static boolean isAllowedImageType(FileUploadSceneEnum scene, String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (scene) {
            case MATERIAL -> normalized.equals("image/gif") || normalized.equals("image/jpeg")
                    || normalized.equals("image/png") || normalized.equals("image/bmp");
            case PRODUCT -> normalized.equals("image/jpeg") || normalized.equals("image/png")
                    || normalized.equals("image/bmp");
            case MERCHANT_QUALIFICATION -> normalized.equals("application/pdf") || normalized.equals("image/jpeg")
                    || normalized.equals("image/png");
            case GENERAL -> true;
        };
    }

    private static boolean isSameImageType(String declaredType, String actualType) {
        return declaredType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim()
                .equals(actualType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim());
    }

}
