package cn.iocoder.yudao.module.trade.service.logistics;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisticsLabelNormalizerTest {

    @Test
    void normalizePdf_rendersExact76x130PixelsAt203Dpi() throws Exception {
        byte[] pdf = createPdf(76, 130);

        byte[] png = new LogisticsLabelNormalizer().normalizePdf(pdf, 76, 130, 203);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(607);
        assertThat(image.getHeight()).isEqualTo(1039);
    }

    @Test
    void normalizePdf_rendersExact100x150PixelsAt203Dpi() throws Exception {
        byte[] pdf = createPdf(100, 150);

        byte[] png = new LogisticsLabelNormalizer().normalizePdf(pdf, 100, 150, 203);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(799);
        assertThat(image.getHeight()).isEqualTo(1199);
    }

    @Test
    void normalizePdf_rejectsTemplateWithDifferentAspectRatio() throws Exception {
        byte[] pdf = createPdf(210, 297);

        assertThatThrownBy(() -> new LogisticsLabelNormalizer().normalizePdf(pdf, 100, 150, 203))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("比例");
    }

    private static byte[] createPdf(float widthMm, float heightMm) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(new PDRectangle(widthMm / 25.4f * 72, heightMm / 25.4f * 72)));
            document.save(output);
            return output.toByteArray();
        }
    }

}
