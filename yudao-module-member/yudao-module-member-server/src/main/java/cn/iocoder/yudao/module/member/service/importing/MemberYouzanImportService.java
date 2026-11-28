package cn.iocoder.yudao.module.member.service.importing;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanImportBatchDO;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanPointSnapshotDO;
import cn.iocoder.yudao.module.member.dal.mysql.importing.MemberYouzanImportBatchMapper;
import cn.iocoder.yudao.module.member.dal.mysql.importing.MemberYouzanPointSnapshotMapper;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelMapper;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@Service
public class MemberYouzanImportService {

    private static final com.fasterxml.jackson.databind.ObjectMapper PAYLOAD_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final int STATUS_PREVIEW = 0;
    private static final int STATUS_IMPORTED = 1;
    private static final int STATUS_EXPIRED = 2;
    private static final int MAX_ROWS = 10_000;

    @Resource
    private MemberYouzanImportBatchMapper batchMapper;
    @Resource
    private MemberYouzanPointSnapshotMapper snapshotMapper;
    @Resource
    private MemberLevelMapper levelMapper;
    @Resource
    private JdbcTemplate jdbc;
    @Value("${mybatis-plus.encryptor.password}")
    private String hashSecret;

    public List<String> listAuthorizedMerchantIds() {
        return jdbc.queryForList("""
                SELECT merchant_id FROM member_youzan_import_grant
                WHERE tenant_id = ? AND operator_id = ?
                ORDER BY merchant_id
                """, Long.class, TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())
                .stream().map(String::valueOf).toList();
    }

    private void requireGrant(Long merchantId, boolean lock) {
        if (merchantId == null || jdbc.queryForList("SELECT operator_id FROM member_youzan_import_grant WHERE tenant_id=? AND merchant_id=? AND operator_id=?" + (lock ? " FOR UPDATE" : ""), Long.class, TenantContextHolder.getRequiredTenantId(), merchantId, SecurityFrameworkUtils.getLoginUserId()).isEmpty()) {
            throw new AccessDeniedException("无此商家的导入授权");
        }
    }

    private Map<String, Long> enabledLevels(boolean lock) {
        var levels = levelMapper.selectList(new LambdaQueryWrapper<MemberLevelDO>().eq(MemberLevelDO::getStatus, 0).orderByAsc(MemberLevelDO::getId).last(lock, "FOR UPDATE"));
        Map<String, Long> byName = new HashMap<>();
        if (levels.size() == 5) {
            for (var level : levels) byName.put(level.getName(), level.getId());
        }
        return byName.size() == 5 ? byName : Map.of();
    }

    private String observed(MemberYouzanPointSnapshotDO snapshot) {
        return snapshot == null ? "absent" : snapshot.getId() + ":" + snapshot.getSnapshotHash() + ":" + snapshot.getRestoreStatus();
    }

    public PreviewResult preview(MultipartFile file, Long merchantId) throws IOException {
        requireGrant(merchantId, false);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传客户模板");
        }
        List<Map<Integer, String>> sourceRows = YouzanMemberWorkbook.read(file);
        if (sourceRows.isEmpty() || !readHeader(sourceRows.get(0))) {
            throw new IllegalArgumentException("客户模板工作表或表头不匹配，请使用原始模板");
        }
        if (sourceRows.size() - 1 > MAX_ROWS) {
            throw new IllegalArgumentException("单次最多导入 10000 行");
        }

        List<SnapshotPayload> payload = new ArrayList<>();
        List<PreviewRow> resultRows = new ArrayList<>();
        Map<String, Integer> firstPointByMobile = new HashMap<>();
        Map<String, Long> levels = enabledLevels(false);
        Set<String> conflicts = YouzanCustomerImportRules.conflictingMobiles(sourceRows.stream().skip(1)
                .filter(row -> !isBlank(row)).map(row -> new YouzanCustomerImportRules.SourceAsset(
                        YouzanCustomerImportRules.normalizeMobile(get(row, 2)), YouzanCustomerImportRules.parseLegacyPoints(get(row, 11)),
                        get(row, 7), get(row, 8))).toList());
        int validCount = 0;
        int invalidCount = 0;
        for (int index = 1; index < sourceRows.size(); index++) {
            Map<Integer, String> row = sourceRows.get(index);
            if (isBlank(row)) {
                continue;
            }
            String mobile = get(row, 2);
            String points = get(row, 11);
            String error = YouzanCustomerImportRules.validateIdentityAndPoints(mobile, points);
            String level = get(row, 8);
            if (error == null && conflicts.contains(YouzanCustomerImportRules.normalizeMobile(mobile))) error = "同一手机号的资产记录冲突，整组跳过";
            if (error == null && level != null && !level.isBlank() && !levels.containsKey(level)) error = "免费等级未匹配当前五档配置";
            if (error == null && get(row, 7) != null && !get(row, 7).isBlank() && parseNonNegativeInteger(get(row, 7)) == null) error = "成长值必须为非负整数";
            String outcome = "待导入快照";
            YouzanCustomerImportRules.SnapshotDisposition disposition = error == null
                    ? YouzanCustomerImportRules.classifySnapshot(mobile, points, firstPointByMobile)
                    : YouzanCustomerImportRules.SnapshotDisposition.INVALID;
            if (disposition == YouzanCustomerImportRules.SnapshotDisposition.CONFLICT) {
                error = "同一手机号对应不同积分快照";
            } else if (disposition == YouzanCustomerImportRules.SnapshotDisposition.DUPLICATE) {
                error = "重复快照，已跳过";
            }
            boolean accepted = disposition == YouzanCustomerImportRules.SnapshotDisposition.NEW;
            if (accepted) {
                validCount++;
                SnapshotPayload item = new SnapshotPayload();
                item.setMobile(YouzanCustomerImportRules.normalizeMobile(mobile));
                item.setPoints(YouzanCustomerImportRules.parseLegacyPoints(points));
                item.setGrowthValue(parseNonNegativeInteger(get(row, 7)));
                item.setFreeLevel(get(row, 8));
                item.setMappedLevelId(level == null || level.isBlank() ? null : levels.get(level));
                var existing = snapshotMapper.findSource(merchantId, YouzanCustomerImportRules.keyedHash(item.getMobile(), hashSecret), false);
                item.setObserved(observed(existing));
                if (existing != null) outcome = Objects.equals(existing.getSnapshotHash(), fingerprint(item)) ? "重复快照，跳过" : "来源快照已变化，仅报告";
                item.setRowNumber(index + 1);
                payload.add(item);
            } else {
                invalidCount++;
            }
            resultRows.add(new PreviewRow(index + 1, YouzanCustomerImportRules.maskMobile(mobile),
                    accepted ? outcome : error));
        }

        if (resultRows.isEmpty()) throw new IllegalArgumentException("客户模板没有数据行");
        MemberYouzanImportBatchDO batch = new MemberYouzanImportBatchDO();
        batch.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
        batch.setTenantId(TenantContextHolder.getRequiredTenantId());
        batch.setMaskedRows(JsonUtils.toJsonString(resultRows));
        batch.setMerchantId(merchantId);
        batch.setStatus(STATUS_PREVIEW);
        batch.setTotalRows(validCount + invalidCount);
        batch.setValidRows(validCount);
        batch.setInvalidRows(invalidCount);
        batch.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        batch.setEncryptedPayload(JsonUtils.toJsonString(payload));
        batchMapper.insert(batch);
        return new PreviewResult(batch.getId(), batch.getExpiresAt(), validCount, invalidCount, resultRows);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirm(Long batchId) {
        MemberYouzanImportBatchDO batch = batchMapper.selectByIdForUpdate(batchId);
        validateOwner(batch);
        requireGrant(batch.getMerchantId(), true);
        if (batch.getStatus() == STATUS_IMPORTED) {
            return toImportResult(batch);
        }
        if (batch.getStatus() != STATUS_PREVIEW) {
            throw new IllegalStateException("预览状态已失效，请重新上传");
        }
        if (batch.getExpiresAt() == null || !batch.getExpiresAt().isAfter(LocalDateTime.now())) {
            batch.setStatus(STATUS_EXPIRED);
            batchMapper.updateById(batch);
            throw new IllegalStateException("预览已过期，请重新上传");
        }

        List<SnapshotPayload> payload;
        try {
            payload = PAYLOAD_JSON.readValue(batch.getEncryptedPayload(), new TypeReference<>() { });
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("批次数据无效，请重新预览");
        }
        int imported = 0;
        int skipped = 0;
        int changed = 0;
        var levels = enabledLevels(true);
        for (SnapshotPayload item : payload) {
            String mobileHash = YouzanCustomerImportRules.keyedHash(item.getMobile(), hashSecret);
            String snapshotHash = fingerprint(item);
            MemberYouzanPointSnapshotDO existing = snapshotMapper.findSource(batch.getMerchantId(), mobileHash, true);
            if (!observed(existing).equals(item.getObserved()) || (item.getMappedLevelId() != null && !Objects.equals(levels.get(item.getFreeLevel()), item.getMappedLevelId()))) {
                throw new IllegalStateException("预览后数据已变化，请重新预览");
            }
            if (existing != null) {
                if (!snapshotHash.equals(existing.getSnapshotHash())) {
                    changed++;
                }
                skipped++;
                continue;
            }
            MemberYouzanPointSnapshotDO snapshot = new MemberYouzanPointSnapshotDO();
            snapshot.setBatchId(batch.getId());
            snapshot.setTenantId(batch.getTenantId());
            snapshot.setMerchantId(batch.getMerchantId());
            snapshot.setMappedLevelId(item.getMappedLevelId());
            snapshot.setMobileHash(mobileHash);
            snapshot.setEncryptedMobile(item.getMobile());
            snapshot.setLegacyPoints(item.getPoints());
            snapshot.setGrowthValue(item.getGrowthValue());
            snapshot.setFreeLevel(item.getFreeLevel());
            snapshot.setSnapshotHash(snapshotHash);
            snapshot.setRestoreStatus(0);
            snapshotMapper.insert(snapshot);
            imported++;
        }
        batch.setStatus(STATUS_IMPORTED);
        batch.setImportedRows(imported);
        batch.setSkippedRows(skipped);
        batch.setChangedRows(changed);
        batch.setEncryptedPayload(null);
        batchMapper.updateById(batch);
        batch.setImportedRows(imported);
        batch.setSkippedRows(skipped);
        batch.setChangedRows(changed);
        return toImportResult(batch);
    }

    public ImportResult getResult(Long batchId) {
        MemberYouzanImportBatchDO batch = batchMapper.selectById(batchId);
        validateOwner(batch);
        requireGrant(batch.getMerchantId(), false);
        return toImportResult(batch);
    }

    private String fingerprint(SnapshotPayload item) {
        return YouzanCustomerImportRules.keyedHash(JsonUtils.toJsonString(java.util.Arrays.asList(item.getMobile(), item.getPoints(), item.getGrowthValue(), item.getFreeLevel(), item.getMappedLevelId())), hashSecret);
    }

    private void validateOwner(MemberYouzanImportBatchDO batch) {
        if (batch == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), batch.getTenantId()) || !Objects.equals(SecurityFrameworkUtils.getLoginUserId(), batch.getOperatorId())) {
            throw new AccessDeniedException("导入批次不可访问");
        }
    }

    private ImportResult toImportResult(MemberYouzanImportBatchDO batch) {
        return new ImportResult(batch.getId(), batch.getStatus(), batch.getTotalRows(), batch.getValidRows(),
                batch.getInvalidRows(), batch.getImportedRows(), batch.getSkippedRows(), batch.getChangedRows(),
                batch.getExpiresAt(), JsonUtils.parseObject(batch.getMaskedRows(), new TypeReference<List<PreviewRow>>() { }));
    }

    private boolean readHeader(Map<Integer, String> header) {
        if (header == null || header.size() != YouzanCustomerImportRules.HEADERS.size()) {
            return false;
        }
        for (int index = 0; index < YouzanCustomerImportRules.HEADERS.size(); index++) {
            if (!YouzanCustomerImportRules.HEADERS.get(index).equals(header.get(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(Map<Integer, String> row) {
        return row == null || row.values().stream().allMatch(value -> value == null || value.isBlank());
    }

    private String get(Map<Integer, String> row, int index) {
        String value = row.get(index);
        return value == null ? null : value.trim();
    }

    private Integer parseNonNegativeInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @lombok.Data
    public static class SnapshotPayload {
        private String mobile;
        private Integer points;
        private Integer growthValue;
        private String freeLevel;
        private Long mappedLevelId;
        private String observed;
        private Integer rowNumber;
    }

    public record PreviewRow(int rowNumber, String maskedMobile, String outcome) { }
    public record PreviewResult(Long batchId, LocalDateTime expiresAt, int validCount, int invalidCount,
                                List<PreviewRow> rows) { }
    public record ImportResult(Long batchId, Integer status, Integer totalRows, Integer validRows,
                               Integer invalidRows, Integer importedRows, Integer skippedRows, Integer changedRows,
                               LocalDateTime expiresAt, List<PreviewRow> rows) { }
}
