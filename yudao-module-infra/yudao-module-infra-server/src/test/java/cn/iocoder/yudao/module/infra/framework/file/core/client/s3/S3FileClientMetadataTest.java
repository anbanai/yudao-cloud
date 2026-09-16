package cn.iocoder.yudao.module.infra.framework.file.core.client.s3;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class S3FileClientMetadataTest {

    @Test
    void buildPutObjectRequest_addsPublicBrowserCachingMetadataForPublicImages() {
        PutObjectRequest request = S3FileClient.buildPutObjectRequest(
                new byte[]{1, 2, 3}, "images/avatar.webp", "image/webp", true);

        assertEquals("image/webp", request.contentType());
        assertEquals(3L, request.contentLength());
        assertEquals("inline", request.contentDisposition());
        assertEquals("public,max-age=31536000,immutable", request.cacheControl());
    }

    @Test
    void buildPutObjectRequest_doesNotSharePrivateImages() {
        PutObjectRequest request = S3FileClient.buildPutObjectRequest(
                new byte[]{1}, "private/avatar.png", "image/png", false);

        assertEquals("inline", request.contentDisposition());
        assertEquals("private,no-store", request.cacheControl());
    }

    @Test
    void buildPutObjectRequest_keepsSvgAsDownload() {
        PutObjectRequest request = S3FileClient.buildPutObjectRequest(
                new byte[]{1}, "images/icon.svg", "image/svg+xml", true);

        assertEquals("attachment", request.contentDisposition());
        assertEquals("public,max-age=31536000,immutable", request.cacheControl());
    }

    @Test
    void buildPutObjectRequest_keepsDownloadMetadataForNonImages() {
        PutObjectRequest request = S3FileClient.buildPutObjectRequest(
                new byte[]{1}, "documents/manual.pdf", "application/pdf", true);

        assertNull(request.contentDisposition());
        assertNull(request.cacheControl());
    }

}
