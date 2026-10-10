package cn.iocoder.yudao.module.infra.framework.file.core.utils;

import cn.iocoder.yudao.module.infra.enums.FileUploadSceneEnum;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_SIZE_EXCEEDED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TYPE_NOT_SUPPORTED;

class FileUploadPolicyTest {

    @Test
    void materialImage_acceptsSupportedFormatsAtTenMegabytesAndRejectsOversizedImage() throws IOException {
        byte[] gif = imageBytes("GIF");

        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.MATERIAL,
                "banner.gif", gif, "image/gif"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.MATERIAL,
                "banner.jpg", imageBytes("JPEG"), "image/jpeg"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.MATERIAL,
                "banner.png", imageBytes("PNG"), "image/png"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.MATERIAL,
                "banner.bmp", imageBytes("BMP"), "image/bmp"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateMetadata(FileUploadSceneEnum.MATERIAL,
                "banner.jpg", "image/jpeg", 10L * 1024 * 1024));
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateImage(
                FileUploadSceneEnum.MATERIAL, "banner.gif", new byte[10 * 1024 * 1024 + 1], "image/gif"),
                FILE_UPLOAD_SIZE_EXCEEDED, "material", 10);
    }

    @Test
    void productImage_acceptsJpgPngBmpAndRejectsGif() throws IOException {
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.PRODUCT,
                "product.jpg", imageBytes("JPEG"), "image/jpeg"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.PRODUCT,
                "product.png", imageBytes("PNG"), "image/png"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateImage(FileUploadSceneEnum.PRODUCT,
                "product.bmp", imageBytes("BMP"), "image/bmp"));
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateImage(
                FileUploadSceneEnum.PRODUCT, "product.gif", imageBytes("GIF"), "image/gif"),
                FILE_UPLOAD_TYPE_NOT_SUPPORTED, "product", FileUploadSceneEnum.PRODUCT.getExtensions());
    }

    @Test
    void productImage_rejectsSizeAtOverThreeMegabytes() {
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateMetadata(
                FileUploadSceneEnum.PRODUCT, "product.jpg", "image/jpeg", 3L * 1024 * 1024 + 1),
                FILE_UPLOAD_SIZE_EXCEEDED, "product", 3);
        assertDoesNotThrow(() -> FileUploadPolicy.validateMetadata(
                FileUploadSceneEnum.PRODUCT, "product.jpg", "image/jpeg", 3L * 1024 * 1024));
    }

    @Test
    void productImage_rejectsContentThatDoesNotMatchExtension() throws IOException {
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateImage(
                FileUploadSceneEnum.PRODUCT, "product.jpg", imageBytes("PNG"), "image/jpeg"),
                FILE_UPLOAD_TYPE_NOT_SUPPORTED, "product", FileUploadSceneEnum.PRODUCT.getExtensions());
    }

    @Test
    void merchantQualification_acceptsPdfAndRejectsMismatchedContent() {
        byte[] pdf = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertDoesNotThrow(() -> FileUploadPolicy.validateQualificationDocument(
                "business-license.pdf", pdf, "application/pdf"));
        assertDoesNotThrow(() -> FileUploadPolicy.validateMetadata(FileUploadSceneEnum.MERCHANT_QUALIFICATION,
                "business-license.pdf", "application/pdf", 10L * 1024 * 1024));
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateMetadata(FileUploadSceneEnum.MERCHANT_QUALIFICATION,
                "business-license.pdf", "application/pdf", 10L * 1024 * 1024 + 1),
                FILE_UPLOAD_SIZE_EXCEEDED, "merchant-qualification", 10);
        AssertUtils.assertServiceException(() -> FileUploadPolicy.validateQualificationDocument(
                "business-license.pdf", imageBytesUnchecked(), "application/pdf"),
                FILE_UPLOAD_TYPE_NOT_SUPPORTED, "merchant-qualification",
                FileUploadSceneEnum.MERCHANT_QUALIFICATION.getExtensions());
    }

    private static byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static byte[] imageBytesUnchecked() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    }

}
