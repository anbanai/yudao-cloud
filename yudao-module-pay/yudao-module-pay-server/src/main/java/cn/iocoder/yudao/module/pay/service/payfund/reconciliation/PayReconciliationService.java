package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.*;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.*;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.*;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.*;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.*;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static cn.iocoder.yudao.module.pay.service.payfund.reconciliation.YeepayBillParser.Type.*;
import static cn.iocoder.yudao.module.pay.enums.fund.PayReconciliationRecordStatusEnum.*;

/** 官方日文件双向核对。仅记录差异，不直接改写资金；网络请求不持有事务。 */
@Service
public class PayReconciliationService {
    @Resource private PayChannelMapper channelMapper;
    @Resource private PayFundOrderMapper fundOrderMapper;
    @Resource private PayFundDetailMapper fundDetailMapper;
    @Resource private PayDivideRequestMapper divideRequestMapper;
    @Resource private PayRefundFundDetailMapper refundFundDetailMapper;
    @Resource private PayRefundMapper refundMapper;
    @Resource private PayReconciliationRecordMapper recordMapper;
    @Resource private PayReconciliationDifferenceMapper differenceMapper;
    @Resource private PayChannelService channelService;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public int processDaily(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!date.isBefore(now.toLocalDate()) || now.getHour() < 9) return 0;
        Set<String> processed = new HashSet<>();
        int count = 0;
        // A disabled channel still owns historical operations. The platform file spans apps/channels.
        for (PayChannelDO channel : channelMapper.selectList(new LambdaQueryWrapper<PayChannelDO>()
                .eq(PayChannelDO::getCode, PayChannelEnum.YEEPAY_WX_LITE.getCode()))) {
            var client = client(channel.getId());
            Set<String> parents = new LinkedHashSet<>();
            parents.add(client.getConfig().getParentMerchantNo());
            parents.addAll(fundOrderMapper.selectHistoricalParentMerchantNos(channel.getId()));
            for (String parent : parents) {
                if (StrUtil.isBlank(parent) || !processed.add(parent + "|" + date)) continue;
                reconcile(channel.getId(), date, parent); count++;
            }
        }
        PageParam recoveryPage = new PageParam(); recoveryPage.setPageNo(1); recoveryPage.setPageSize(100);
        for (var unfinished : recordMapper.selectPage(recoveryPage, new LambdaQueryWrapper<PayReconciliationRecordDO>()
                .in(PayReconciliationRecordDO::getStatus, PENDING.getStatus(), PROCESSING.getStatus())
                .lt(PayReconciliationRecordDO::getReconciliationDate, now.toLocalDate())
                .orderByAsc(PayReconciliationRecordDO::getId)).getList()) {
            if (processed.add(unfinished.getMerchantNo() + "|" + unfinished.getReconciliationDate())) {
                reconcile(unfinished.getChannelId(), unfinished.getReconciliationDate(), unfinished.getMerchantNo()); count++;
            }
        }
        return count;
    }

    public Long reconcile(Long channelId, LocalDate date, String parentMerchantNo) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (date == null || !date.isBefore(now.toLocalDate()) || now.getHour() < 9
                || StrUtil.isBlank(parentMerchantNo)) throw new IllegalArgumentException("请在次日 09:00 后对原平台商编日账单对账");
        PayChannelDO channel = channelMapper.selectById(channelId);
        if (channel == null || !PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(channel.getCode())) {
            throw new IllegalArgumentException("易宝渠道不存在");
        }
        var scopedClient = client(channelId);
        if (!parentMerchantNo.equals(scopedClient.getConfig().getParentMerchantNo())
                && !fundOrderMapper.selectHistoricalParentMerchantNos(channelId).contains(parentMerchantNo)) {
            throw new IllegalArgumentException("原平台商编不属于本租户渠道资金快照");
        }
        String batchNo = "YPB-" + hash(TenantContextHolder.getTenantId() + "|" + parentMerchantNo + "|" + date);
        PayReconciliationRecordDO record = recordMapper.selectByBatchNo(batchNo);
        if (record == null) {
            record = new PayReconciliationRecordDO().setBatchNo(batchNo).setChannelId(channelId)
                    .setAppId(channel.getAppId()).setChannelCode(channel.getCode()).setMerchantNo(parentMerchantNo)
                    .setReconciliationDate(date).setStatus(PENDING.getStatus());
            try { recordMapper.insert(record); }
            catch (DuplicateKeyException race) { record = recordMapper.selectByBatchNo(batchNo); }
        }
        if (record == null) throw new IllegalStateException("对账任务创建失败");
        if (COMPLETED.getStatus().equals(record.getStatus())) return record.getId();
        if (recordMapper.update(new PayReconciliationRecordDO().setStatus(PROCESSING.getStatus()).setStartedTime(now),
                new LambdaUpdateWrapper<PayReconciliationRecordDO>().eq(PayReconciliationRecordDO::getId, record.getId())
                        .and(w -> w.ne(PayReconciliationRecordDO::getStatus, PROCESSING.getStatus())
                                .or().lt(PayReconciliationRecordDO::getStartedTime, now.minusMinutes(30)))) != 1) return record.getId();
        try {
            YeepayPayClient client = client(channelId);
            byte[] trade = client.downloadDailyBill(date, false, parentMerchantNo);
            byte[] divide = client.downloadDailyBill(date, true, parentMerchantNo);
            List<YeepayBillParser.Entry> entries = new ArrayList<>(YeepayBillParser.parse(trade, false, date, parentMerchantNo));
            entries.addAll(YeepayBillParser.parse(divide, true, date, parentMerchantNo));
            Map<String, YeepayBillComparator.Local> expected = dailyLocal(date, parentMerchantNo);
            // Lookup external request IDs as well, so wrong local state/date is a discrepancy rather than an orphan.
            for (var e : entries) {
                if (expected.containsKey(e.key())) continue;
                var resolved = resolve(e, parentMerchantNo);
                if (resolved != null) expected.put(e.key(), resolved);
            }
            var result = YeepayBillComparator.compare(entries, new ArrayList<>(expected.values()), date);
            long differenceAmount = 0;
            for (var d : result.differences()) {
                long local = d.local() == null ? 0 : d.local().entry().amount();
                long remote = d.channel() == null ? 0 : d.channel().amount();
                long delta = Math.abs(Math.subtractExact(local, remote));
                differenceAmount = Math.addExact(differenceAmount, delta);
                var e = d.channel() == null ? d.local().entry() : d.channel();
                saveDifference(record.getId(), e.type().name(), d.key() + "|local=" + (d.local() == null ? "" : d.local().entry()), e, local, remote, d.reason());
            }
            differenceAmount = Math.addExact(differenceAmount,
                    checkClearing(record.getId(), client, entries, expected.values(), parentMerchantNo));
            boolean unresolved = differenceMapper.selectCount(new LambdaQueryWrapper<PayReconciliationDifferenceDO>()
                    .eq(PayReconciliationDifferenceDO::getRecordId, record.getId()).eq(PayReconciliationDifferenceDO::getStatus, 0)) > 0;
            long total = 0;
            for (var e : entries) total = Math.addExact(total, e.amount());
            finish(record.getId(), now, new PayReconciliationRecordDO().setStatus(unresolved ? MANUAL_REQUIRED.getStatus() : COMPLETED.getStatus())
                    .setTotalCount(entries.size()).setTotalAmount(total).setTotalPrice(total)
                    .setMatchedCount(result.matchedCount()).setMatchedAmount(result.matchedAmount()).setDifferenceAmount(differenceAmount)
                    .setRawData("tradeSha256=" + hash(trade) + ";divideSha256=" + hash(divide))
                    .setCompletedTime(LocalDateTime.now(clock)));
        } catch (Exception failure) {
            // Do not persist SDK cause text/raw bills, which may contain personal or signature data.
            saveDifference(record.getId(), "BILL_DOWNLOAD_OR_SCHEMA", "download", null, 0, 0,
                    "下载、格式或本地数据校验失败：" + failure.getClass().getSimpleName());
            finish(record.getId(), now, new PayReconciliationRecordDO().setStatus(MANUAL_REQUIRED.getStatus())
                    .setErrorMsg("官方账单下载或校验失败，需人工复核").setCompletedTime(LocalDateTime.now(clock)));
        }
        return record.getId();
    }

    private Map<String, YeepayBillComparator.Local> dailyLocal(LocalDate date, String parent) {
        Map<String, YeepayBillComparator.Local> local = new LinkedHashMap<>();
        LocalDateTime start = date.atStartOfDay(), end = date.plusDays(1).atStartOfDay();
        for (var f : fundOrderMapper.selectList(new LambdaQueryWrapper<PayFundOrderDO>()
                .eq(PayFundOrderDO::getParentMerchantNo, parent).ge(PayFundOrderDO::getSuccessTime, start).lt(PayFundOrderDO::getSuccessTime, end))) {
            add(local, payment(f));
        }
        for (var r : refundMapper.selectList(new LambdaQueryWrapper<PayRefundDO>()
                .eq(PayRefundDO::getStatus, PayRefundStatusEnum.SUCCESS.getStatus())
                .ge(PayRefundDO::getSuccessTime, start).lt(PayRefundDO::getSuccessTime, end))) {
            var f = fundOrderMapper.selectByPayOrderId(r.getOrderId());
            if (f != null && Objects.equals(parent, f.getParentMerchantNo())) add(local, refund(r, f));
        }
        for (var d : fundDetailMapper.selectList(new LambdaQueryWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getStatus, PayFundDetailStatusEnum.SUCCESS.getStatus())
                .ge(PayFundDetailDO::getSuccessTime, start).lt(PayFundDetailDO::getSuccessTime, end))) {
            var f = fundOrderMapper.selectById(d.getFundOrderId());
            if (f != null && Objects.equals(parent, f.getParentMerchantNo()) && amount(d) > 0) add(local, divide(d, f));
        }
        for (var r : refundFundDetailMapper.selectList(new LambdaQueryWrapper<PayRefundFundDetailDO>()
                .eq(PayRefundFundDetailDO::getDivideBackStatus, PayRefundFundDetailStatusEnum.SUCCESS.getStatus())
                .ge(PayRefundFundDetailDO::getDivideBackSuccessTime, start).lt(PayRefundFundDetailDO::getDivideBackSuccessTime, end))) {
            var f = fundOrderMapper.selectById(r.getFundOrderId());
            if (f != null && Objects.equals(parent, f.getParentMerchantNo()) && StrUtil.isNotBlank(r.getDivideBackRequestId())) {
                add(local, returned(r, fundDetailMapper.selectById(r.getFundDetailId()), f));
            }
        }
        return local;
    }

    private YeepayBillComparator.Local resolve(YeepayBillParser.Entry e, String parent) {
        switch (e.type()) {
            case PAYMENT -> { var f = fundOrderMapper.selectByPayOrderNo(e.requestNo()); return !belongs(f, parent) ? null : payment(f); }
            case REFUND -> { var r = refundMapper.selectByNo(e.requestNo()); if (r == null) return null;
                var f = fundOrderMapper.selectByPayOrderId(r.getOrderId()); return !belongs(f, parent) ? null : refund(r, f); }
            case DIVIDE -> { var req = divideRequestMapper.selectByRequestNo(e.requestNo()); if (req == null) return null;
                var f = fundOrderMapper.selectById(req.getFundOrderId()); if (!belongs(f, parent)) return null;
                var details = fundDetailMapper.selectListByFundOrderId(f.getId()).stream()
                        .filter(d -> Objects.equals(d.getMerchantNo(), e.receiverMerchantNo())).toList();
                if (details.size() != 1) return null; return divide(details.get(0), f); }
            case DIVIDE_BACK -> { var rows = refundFundDetailMapper.selectListByDivideBackRequestId(e.requestNo());
                for (var r : rows) { var d = fundDetailMapper.selectById(r.getFundDetailId());
                    if (d != null && Objects.equals(d.getMerchantNo(), e.receiverMerchantNo())) {
                        var f = fundOrderMapper.selectById(r.getFundOrderId()); return !belongs(f, parent) ? null : returned(r, d, f);
                    } } return null; }
            default -> throw new IllegalStateException("未知业务类型");
        }
    }

    private YeepayBillComparator.Local payment(PayFundOrderDO f) {
        return new YeepayBillComparator.Local(new YeepayBillParser.Entry(PAYMENT, f.getCollectionMerchantNo(),
                f.getPayOrderNo(), f.getChannelOrderNo(), f.getPayOrderNo(), null, null, f.getAmount(), f.getSuccessTime()), f.getSuccessTime() != null);
    }
    private YeepayBillComparator.Local refund(PayRefundDO r, PayFundOrderDO f) {
        return new YeepayBillComparator.Local(new YeepayBillParser.Entry(REFUND, f.getCollectionMerchantNo(),
                f.getPayOrderNo(), r.getChannelRefundNo(), r.getNo(), null, null, r.getRefundPrice(), r.getSuccessTime()),
                PayRefundStatusEnum.SUCCESS.getStatus().equals(r.getStatus()));
    }
    private YeepayBillComparator.Local divide(PayFundDetailDO d, PayFundOrderDO f) {
        var request = divideRequestMapper.selectByFundOrderId(f.getId());
        if (request == null) throw new IllegalStateException("原分账请求缺失");
        return new YeepayBillComparator.Local(new YeepayBillParser.Entry(DIVIDE, f.getCollectionMerchantNo(),
                f.getPayOrderNo(), f.getChannelOrderNo(), request.getRequestNo(), d.getChannelDetailNo(), d.getMerchantNo(), amount(d), d.getSuccessTime()),
                PayFundDetailStatusEnum.SUCCESS.getStatus().equals(d.getStatus()));
    }
    private YeepayBillComparator.Local returned(PayRefundFundDetailDO r, PayFundDetailDO d, PayFundOrderDO f) {
        if (d == null) throw new IllegalStateException("原归还资金明细缺失");
        return new YeepayBillComparator.Local(new YeepayBillParser.Entry(DIVIDE_BACK, f.getCollectionMerchantNo(),
                f.getPayOrderNo(), f.getChannelOrderNo(), r.getDivideBackRequestId(), null, d.getMerchantNo(), r.getAmount(), r.getDivideBackSuccessTime()),
                PayRefundFundDetailStatusEnum.SUCCESS.getStatus().equals(r.getDivideBackStatus()));
    }
    private static boolean belongs(PayFundOrderDO f, String parent) { return f != null && Objects.equals(parent, f.getParentMerchantNo()); }
    private static int amount(PayFundDetailDO d) { return d.getDivideAmount() == null ? d.getAmount() : d.getDivideAmount(); }
    private static void add(Map<String, YeepayBillComparator.Local> map, YeepayBillComparator.Local entry) {
        if (map.putIfAbsent(entry.entry().key(), entry) != null) throw new IllegalStateException("本地流水重复");
        if (map.size() > 100000) throw new IllegalStateException("本地日流水超过上限");
    }

    private long checkClearing(Long recordId, YeepayPayClient client, List<YeepayBillParser.Entry> external,
                               Collection<YeepayBillComparator.Local> local, String parent) {
        long differenceAmount = 0;
        Set<String> orders = new LinkedHashSet<>();
        for (var e : external) if (e.type() == PAYMENT) orders.add(e.requestNo());
        for (var l : local) if (l.entry().type() == PAYMENT) orders.add(l.entry().requestNo());
        for (String no : orders) {
            var f = fundOrderMapper.selectByPayOrderNo(no); if (!belongs(f, parent)) continue;
            var r = client.queryClearing(no, f.getChannelOrderNo(), f.getParentMerchantNo(), f.getCollectionMerchantNo());
            long remoteAmount;
            try { remoteAmount = new BigDecimal(String.valueOf(r.get("orderAmount"))).movePointRight(2).longValueExact(); }
            catch (ArithmeticException | NumberFormatException invalid) { remoteAmount = 0; }
            boolean identityMatches = Objects.equals(no, r.get("orderId"))
                    && Objects.equals(f.getParentMerchantNo(), r.get("parentMerchantNo"))
                    && Objects.equals(f.getCollectionMerchantNo(), r.get("merchantNo"))
                    && StrUtil.isNotBlank(f.getChannelOrderNo()) && Objects.equals(f.getChannelOrderNo(), r.get("uniqueOrderNo"));
            String status = String.valueOf(r.get("clearStatus"));
            Integer state = switch (status) { case "SUCCESS", "SUCCEED", "COMPLETED" -> PayFundOrderStatusEnum.SUCCESS.getStatus();
                case "PROCESSING", "WAIT", "WAITING", "PENDING", "NOT_CLEAR" -> PayFundOrderStatusEnum.PROCESSING.getStatus();
                case "FAIL", "FAILED", "FAILURE" -> PayFundOrderStatusEnum.FAILED.getStatus(); default -> null; };
            boolean pendingLocal = Objects.equals(f.getClearingStatus(), PayFundOrderStatusEnum.PENDING.getStatus())
                    || Objects.equals(f.getClearingStatus(), PayFundOrderStatusEnum.PROCESSING.getStatus());
            if (!identityMatches || remoteAmount != f.getAmount() || state == null || !(Objects.equals(state, f.getClearingStatus())
                    || pendingLocal && Objects.equals(state, PayFundOrderStatusEnum.PROCESSING.getStatus()))) {
                String evidence = hash(no + "|" + f.getClearingStatus() + "|" + r.get("clearStatus") + "|"
                        + r.get("orderId") + "|" + r.get("parentMerchantNo") + "|" + r.get("merchantNo") + "|" + r.get("uniqueOrderNo"));
                saveDifference(recordId, "CLEARING", no + "|" + evidence, null, f.getAmount(), remoteAmount,
                        "主动清算查询身份、金额或状态与原资金快照不一致或未知");
                differenceAmount = Math.addExact(differenceAmount, Math.abs(Math.subtractExact(f.getAmount(), remoteAmount)));
            }
        }
        return differenceAmount;
    }

    private void saveDifference(Long recordId, String type, String key, YeepayBillParser.Entry e,
                                long local, long remote, String reason) {
        String identity = hash(recordId + "|" + type + "|" + key + "|" + local + "|" + remote + "|" + reason
                + "|" + (e == null ? "" : e.merchantNo() + "|" + e.externalNo() + "|" + e.detailNo()));
        var difference = new PayReconciliationDifferenceDO().setRecordId(recordId).setDifferenceKey(identity)
                .setBizType(type).setRequestNo(e == null ? key : e.requestNo()).setOrderNo(e == null ? null : e.orderId())
                .setMerchantNo(e == null ? null : e.merchantNo()).setDetailNo(e == null ? null : e.detailNo())
                .setLocalAmount(local).setChannelAmount(remote).setDifferenceAmount(Math.abs(Math.subtractExact(local, remote)))
                .setReason(reason).setStatus(0);
        try { differenceMapper.insert(difference); } catch (DuplicateKeyException existing) { /* Preserve original anomaly and audit. */ }
    }
    private void finish(Long id, LocalDateTime claim, PayReconciliationRecordDO update) {
        recordMapper.update(update, new LambdaUpdateWrapper<PayReconciliationRecordDO>().eq(PayReconciliationRecordDO::getId, id)
                .eq(PayReconciliationRecordDO::getStatus, PROCESSING.getStatus()).eq(PayReconciliationRecordDO::getStartedTime, claim));
    }
    private YeepayPayClient client(Long id) {
        if (!(channelService.getPayClient(id) instanceof YeepayPayClient client)) throw new IllegalArgumentException("易宝客户端不存在");
        return client;
    }
    private static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public PageResult<PayReconciliationRecordDO> page(PageParam page, Integer status, LocalDate date) {
        return recordMapper.selectPage(page, new LambdaQueryWrapper<PayReconciliationRecordDO>()
                .eq(status != null, PayReconciliationRecordDO::getStatus, status)
                .eq(date != null, PayReconciliationRecordDO::getReconciliationDate, date).orderByDesc(PayReconciliationRecordDO::getId));
    }
    public PayReconciliationRecordDO get(Long id) { return recordMapper.selectById(id); }
    public PageResult<PayReconciliationDifferenceDO> differences(PageParam page, Long recordId, Integer status) {
        return differenceMapper.selectPage(page, new LambdaQueryWrapper<PayReconciliationDifferenceDO>()
                .eq(PayReconciliationDifferenceDO::getRecordId, recordId)
                .eq(status != null, PayReconciliationDifferenceDO::getStatus, status).orderByDesc(PayReconciliationDifferenceDO::getId));
    }
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, Long operator, String reason, String evidence) {
        validateAudit(operator, reason, evidence);
        var current = differenceMapper.selectById(id);
        if (current == null || !Integer.valueOf(0).equals(current.getStatus())) throw new IllegalArgumentException("待处理对账差异不存在");
        int changed = differenceMapper.update(new PayReconciliationDifferenceDO().setStatus(10).setOperatorId(operator)
                .setResolutionReason(reason).setExternalEvidence(evidence).setResolvedTime(LocalDateTime.now(clock)),
                new LambdaUpdateWrapper<PayReconciliationDifferenceDO>().eq(PayReconciliationDifferenceDO::getId, id).eq(PayReconciliationDifferenceDO::getStatus, 0));
        if (changed != 1) throw new IllegalStateException("对账差异已被处理");
    }
    public Long retry(Long channelId, LocalDate date, String parent, Long operator, String reason, String evidence) {
        validateAudit(operator, reason, evidence);
        Long id = reconcile(channelId, date, parent);
        differenceMapper.insert(new PayReconciliationDifferenceDO().setRecordId(id).setDifferenceKey(UUID.randomUUID().toString())
                .setBizType("MANUAL_RETRY").setStatus(10).setOperatorId(operator).setResolutionReason(reason)
                .setExternalEvidence(evidence).setResolvedTime(LocalDateTime.now(clock)));
        return id;
    }
    private static void validateAudit(Long operator, String reason, String evidence) {
        if (operator == null || operator <= 0 || StrUtil.isBlank(reason) || StrUtil.isBlank(evidence)) throw new IllegalArgumentException("必须记录处理人、原因和外部凭证");
    }
}
