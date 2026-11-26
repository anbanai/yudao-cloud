package cn.iocoder.yudao.module.member.service.importing;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed source-template and safe normalization rules for Youzan customer snapshots. */
public final class YouzanCustomerImportRules {

    public static final String SHEET_NAME = "客户测试名单";
    public static final List<String> HEADERS = List.of(
            "客户姓名", "客户昵称", "客户手机号", "客户生日", "客户性别", "标签", "是否会员", "成长值", "免费等级", "付费等级",
            "权益卡", "客户积分", "客户储值余额", "消费次数", "消费金额", "累计退款金额", "累计退款订单数", "笔单价", "上次消费时间",
            "分销员", "导购员", "企微好友", "来源渠道", "来源方式", "成为客户时间", "成为会员时间", "成为会员店铺", "公众号",
            "公众号关注时间", "最近浏览时间", "微信地域", "所属区域", "指定时间消费次数", "指定时间消费金额", "yz_open_id",
            "微信公众号openid", "微信公众号unionid", "微信小程序openid", "微信小程序unionid");

    private static final Pattern MAINLAND_MOBILE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern INTEGER = Pattern.compile("(?:0|[1-9]\\d*)");

    private YouzanCustomerImportRules() {
    }

    public static boolean hasExpectedHeaders(List<String> headers) {
        return HEADERS.equals(headers);
    }

    public static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String normalized = mobile.trim().replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        }
        return MAINLAND_MOBILE.matcher(normalized).matches() ? normalized : null;
    }

    public static Integer parseLegacyPoints(String points) {
        if (points == null) {
            return null;
        }
        String normalized = points.trim();
        if (!INTEGER.matcher(normalized).matches()) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String maskMobile(String mobile) {
        String normalized = normalizeMobile(mobile);
        return normalized == null ? "***" : normalized.substring(0, 3) + "****" + normalized.substring(7);
    }

    /** Returns only a generic, non-PII row error suitable for preview and batch results. */
    public static String validateIdentityAndPoints(String mobile, String points) {
        if (normalizeMobile(mobile) == null) {
            return "手机号缺失或格式无效";
        }
        if (parseLegacyPoints(points) == null) {
            return "客户积分必须为非负整数";
        }
        return null;
    }

    /** Classifies repeated normalized identities within one uploaded snapshot. */
    public static SnapshotDisposition classifySnapshot(String mobile, String points,
                                                       Map<String, Integer> firstPointByMobile) {
        String error = validateIdentityAndPoints(mobile, points);
        if (error != null) {
            return SnapshotDisposition.INVALID;
        }
        String normalizedMobile = normalizeMobile(mobile);
        Integer point = parseLegacyPoints(points);
        Integer firstPoint = firstPointByMobile.putIfAbsent(normalizedMobile, point);
        if (firstPoint == null) {
            return SnapshotDisposition.NEW;
        }
        return firstPoint.equals(point) ? SnapshotDisposition.DUPLICATE : SnapshotDisposition.CONFLICT;
    }

    public record SourceAsset(String mobile, Integer points, String growth, String level) { }

    public static java.util.Set<String> conflictingMobiles(List<SourceAsset> assets) {
        var first = new java.util.HashMap<String, SourceAsset>();
        var conflicts = new java.util.HashSet<String>();
        for (var asset : assets) {
            if (asset.mobile() == null) continue;
            var previous = first.putIfAbsent(asset.mobile(), asset);
            if (previous != null && !previous.equals(asset)) conflicts.add(asset.mobile());
        }
        return conflicts;
    }

    public static String keyedHash(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成快照摘要", exception);
        }
    }

    public enum SnapshotDisposition {
        NEW,
        DUPLICATE,
        CONFLICT,
        INVALID
    }
}
