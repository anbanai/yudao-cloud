package cn.iocoder.yudao.module.trade.service.logistics;

import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** 将供应商 PDF 面单规范化为热敏打印机的精确像素 PNG。 */
@Component
public class LogisticsLabelNormalizer {

    private static final double MAX_ASPECT_RATIO_ERROR = 0.01D;

    @SneakyThrows
    public byte[] normalizePdf(byte[] pdf, int widthMm, int heightMm, int dpi) {
        if (pdf == null || pdf.length == 0 || widthMm <= 0 || heightMm <= 0 || dpi <= 0) {
            throw new IllegalArgumentException("面单内容、纸张尺寸和 DPI 必须有效");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() != 1) {
                throw new IllegalArgumentException("电子面单必须只有一页");
            }
            PDPage page = document.getPage(0);
            double sourceRatio = page.getCropBox().getWidth() / page.getCropBox().getHeight();
            double targetRatio = (double) widthMm / heightMm;
            if (Math.abs(sourceRatio / targetRatio - 1D) > MAX_ASPECT_RATIO_ERROR) {
                throw new IllegalArgumentException("顺丰面单模板比例与目标纸张比例不一致");
            }
            BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(0, dpi, ImageType.RGB);
            int targetWidth = (int) Math.round((double) widthMm / 25.4D * dpi);
            int targetHeight = (int) Math.round((double) heightMm / 25.4D * dpi);
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.min((double) targetWidth / rendered.getWidth(),
                        (double) targetHeight / rendered.getHeight());
                int drawWidth = (int) Math.round(rendered.getWidth() * scale);
                int drawHeight = (int) Math.round(rendered.getHeight() * scale);
                graphics.drawImage(rendered, (targetWidth - drawWidth) / 2, (targetHeight - drawHeight) / 2,
                        drawWidth, drawHeight, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(target, "png", output);
            return output.toByteArray();
        }
    }

}
