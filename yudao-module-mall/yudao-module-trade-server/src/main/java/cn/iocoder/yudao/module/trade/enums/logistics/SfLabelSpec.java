package cn.iocoder.yudao.module.trade.enums.logistics;

import java.util.Arrays;

/** 顺丰云打印与 GS050DY 支持的标签规格。 */
public enum SfLabelSpec {

    FM_76_130(76, 130, 203),
    FM_100_150(100, 150, 203);

    private final int widthMm;
    private final int heightMm;
    private final int dpi;

    SfLabelSpec(int widthMm, int heightMm, int dpi) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.dpi = dpi;
    }

    public int getWidthMm() {
        return widthMm;
    }

    public int getHeightMm() {
        return heightMm;
    }

    public int getDpi() {
        return dpi;
    }

    public int getWidthPixels() {
        return pixels(widthMm, dpi);
    }

    public int getHeightPixels() {
        return pixels(heightMm, dpi);
    }

    public static boolean supports(Integer widthMm, Integer heightMm, Integer dpi) {
        if (widthMm == null || heightMm == null || dpi == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(spec -> spec.widthMm == widthMm
                && spec.heightMm == heightMm && spec.dpi == dpi);
    }

    public static SfLabelSpec of(int widthMm, int heightMm, int dpi) {
        return Arrays.stream(values()).filter(spec -> spec.widthMm == widthMm
                        && spec.heightMm == heightMm && spec.dpi == dpi)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("仅支持 76x130 或 100x150 mm，203 DPI"));
    }

    private static int pixels(int millimeters, int dpi) {
        return (int) Math.round((double) millimeters / 25.4D * dpi);
    }
}
