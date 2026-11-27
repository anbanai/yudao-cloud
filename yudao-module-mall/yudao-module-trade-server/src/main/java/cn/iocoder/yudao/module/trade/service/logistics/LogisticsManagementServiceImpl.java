package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.SfLabelSpec;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
public class LogisticsManagementServiceImpl implements LogisticsManagementService {

    private static final String ACTIVE_ENROLLMENT_KEY = "ACTIVE";
    private static final int ENROLLMENT_TTL_MINUTES = 10;

    @Resource private TradeLogisticsAccountMapper accountMapper;
    @Resource private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Resource private DeliveryExpressMapper expressMapper;
    @Resource private FileApi fileApi;
    @Resource private TradeLogisticsPrintTaskMapper taskMapper;
    @Resource private PrintBridgeConfigFileGenerator configFileGenerator;
    @Value("${yudao.trade.logistics.printbridge.task-endpoint:}")
    private String printBridgeTaskEndpoint;
    @Value("${yudao.trade.logistics.printbridge.merchant-origin:}")
    private String printBridgeMerchantOrigin;

    @Override
    public List<SfLogisticsAccountRespVO> getAccounts() {
        return accountMapper.selectListAll().stream().map(this::toAccountResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccount(SfLogisticsAccountSaveReqVO request) {
        DeliveryExpressDO sfExpress = expressMapper.selectByCode("SF");
        if (sfExpress == null) throw exception(LOGISTICS_SF_API_FAILED, "系统未配置顺丰快递公司（SF）");
        TradeLogisticsAccountDO account = request.getId() == null ? new TradeLogisticsAccountDO()
                : accountMapper.selectById(request.getId());
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        if (request.getId() == null && (StrUtil.isBlank(request.getPartnerId())
                || StrUtil.isBlank(request.getCheckWord()))) {
            throw exception(LOGISTICS_SF_API_FAILED, "新建账号必须填写 Partner ID 和校验码");
        }
        account.setName(request.getName()).setLogisticsId(sfExpress.getId())
                .setEndpoint(cn.iocoder.yudao.module.trade.framework.logistics.sf.SfOpenApiClient.PRODUCTION_ENDPOINT)
                .setDefaultFlag(Integer.valueOf(0).equals(request.getStatus()) && Boolean.TRUE.equals(request.getDefaultFlag()))
                .setStatus(request.getStatus());
        if (StrUtil.isNotBlank(request.getPartnerId()) && !StrUtil.equals(account.getPartnerId(), request.getPartnerId())) {
            account.setPartnerId(request.getPartnerId()).setTemplateCode(null);
        }
        if (StrUtil.isNotBlank(request.getCheckWord())) account.setCheckWord(request.getCheckWord());
        if (Boolean.TRUE.equals(account.getDefaultFlag())) accountMapper.clearDefault(account.getId());
        if (account.getId() == null) accountMapper.insert(account); else accountMapper.updateById(account);
        return account.getId();
    }

    @Override
    public List<LogisticsPrintDeviceRespVO> getDevices() {
        return deviceMapper.selectListAll().stream().map(this::toDeviceResp).toList();
    }

    @Override
    public List<LogisticsPrintDeviceRespVO> getMerchantDevices(Long merchantId) {
        return deviceMapper.selectListByMerchantId(merchantId).stream().map(this::toDeviceResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsPrintDeviceRespVO saveDevice(Long merchantId, LogisticsPrintDeviceSaveReqVO request) {
        TradeLogisticsPrintDeviceDO device = requireOwnedDevice(merchantId, request.getId());
        device.setPrinterName(StrUtil.blankToDefault(request.getPrinterName(), device.getPrinterName()))
                .setDefaultFlag(Boolean.TRUE.equals(request.getDefaultFlag()) && Integer.valueOf(0).equals(request.getStatus()))
                .setStatus(request.getStatus());
        if (Boolean.TRUE.equals(device.getDefaultFlag())) deviceMapper.clearDefault(merchantId, device.getId());
        deviceMapper.updateSettings(device);
        return toDeviceResp(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsPrintDeviceRespVO enrollDevice(Long merchantId) {
        validatePrintBridgeConfiguration();
        LocalDateTime now = LocalDateTime.now();
        TradeLogisticsPrintDeviceDO device = deviceMapper.selectPendingEnrollmentForUpdate(merchantId);
        if (device != null && device.getTokenCreatedTime() != null
                && device.getTokenCreatedTime().isAfter(now.minusMinutes(ENROLLMENT_TTL_MINUTES))) {
            throw exception(LOGISTICS_DEVICE_ENROLLMENT_PENDING);
        }
        String token = LogisticsTokenUtils.generate();
        String deviceCode = UUID.randomUUID().toString();
        String deviceName = "打印工作站-" + StrUtil.subSuf(deviceCode, deviceCode.length() - 6);
        if (device == null) {
            device = new TradeLogisticsPrintDeviceDO().setMerchantId(merchantId).setDeviceCode(deviceCode)
                    .setDeviceName(deviceName).setEnrollmentKey(ACTIVE_ENROLLMENT_KEY)
                    .setDefaultFlag(deviceMapper.selectDefaultEnabled(merchantId) == null).setStatus(0);
            device.setTokenHash(LogisticsTokenUtils.hash(token)).setTokenCreatedTime(now);
            try {
                deviceMapper.insert(device);
            } catch (DuplicateKeyException duplicateException) {
                throw exception(LOGISTICS_DEVICE_ENROLLMENT_PENDING);
            }
        } else {
            device.setDeviceCode(deviceCode).setDeviceName(deviceName).setEnrollmentKey(ACTIVE_ENROLLMENT_KEY)
                    .setTokenHash(LogisticsTokenUtils.hash(token)).setTokenCreatedTime(now);
            deviceMapper.updateById(device);
        }
        String configFile = configFileGenerator.generate(printBridgeTaskEndpoint, printBridgeMerchantOrigin, token,
                deviceCode, deviceName);
        return toDeviceResp(device).setPending(true).setConfigFile(configFile);
    }

    @Override
    @SneakyThrows
    public LogisticsPrintTaskRespVO createTestPrint(Long merchantId, Long deviceId, int paperWidthMm, int paperHeightMm) {
        TradeLogisticsPrintDeviceDO device = requireOwnedDevice(merchantId, deviceId);
        if (!Integer.valueOf(0).equals(device.getStatus()) || isPending(device) || StrUtil.isBlank(device.getPrinterName())) {
            throw exception(LOGISTICS_DEVICE_NOT_READY);
        }
        SfLabelSpec spec = SfLabelSpec.of(paperWidthMm, paperHeightMm, 203);
        BufferedImage image = new BufferedImage(spec.getWidthPixels(), spec.getHeightPixels(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK); graphics.setStroke(new BasicStroke(4));
            graphics.drawRect(8, 8, image.getWidth() - 17, image.getHeight() - 17);
            int margin = Math.max(40, image.getWidth() / 14);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(32, image.getWidth() / 16)));
            graphics.drawString("PrintBridge " + paperWidthMm + " x " + paperHeightMm + " mm", margin, 120);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(24, image.getWidth() / 24)));
            graphics.drawString("203 DPI / " + image.getWidth() + " x " + image.getHeight() + " px", margin, 190);
            for (int y = 280; y < image.getHeight() - 80; y += 100) {
                graphics.drawLine(margin, y, image.getWidth() - margin, y);
            }
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        byte[] data = output.toByteArray();
        FileCreateRespDTO file = fileApi.createPrivateFile(data,
                "printbridge-test-" + paperWidthMm + "x" + paperHeightMm + ".png",
                "trade/logistics/" + TenantContextHolder.getRequiredTenantId() + "/" + merchantId + "/diagnostics", "image/png");
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO()
                .setMerchantId(merchantId).setDeviceId(deviceId)
                .setRequestId("REQ-" + UUID.randomUUID()).setJobId("JOB-" + UUID.randomUUID())
                .setFormat("image").setLabelFileId(file.getId()).setLabelUrl(file.getUrl())
                .setPaperWidthMm(paperWidthMm).setPaperHeightMm(paperHeightMm).setDpi(203).setCopies(1)
                .setChecksum(DigestUtil.sha256Hex(data)).setTestFlag(true).setStatus(LogisticsPrintTaskStatusEnum.PENDING.name());
        taskMapper.insert(task);
        return BeanUtils.toBean(task, LogisticsPrintTaskRespVO.class);
    }

    private SfLogisticsAccountRespVO toAccountResp(TradeLogisticsAccountDO account) {
        return new SfLogisticsAccountRespVO().setId(account.getId()).setName(account.getName())
                .setPartnerIdMasked(mask(account.getPartnerId()))
                .setDefaultFlag(account.getDefaultFlag()).setStatus(account.getStatus());
    }

    private TradeLogisticsPrintDeviceDO requireOwnedDevice(Long merchantId, Long deviceId) {
        TradeLogisticsPrintDeviceDO device = deviceMapper.selectByIdAndMerchantId(deviceId, merchantId);
        if (device == null) {
            throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        }
        return device;
    }

    private LogisticsPrintDeviceRespVO toDeviceResp(TradeLogisticsPrintDeviceDO device) {
        return new LogisticsPrintDeviceRespVO().setId(device.getId()).setMerchantId(device.getMerchantId()).setDeviceCode(device.getDeviceCode())
                .setDeviceName(device.getDeviceName()).setDefaultFlag(device.getDefaultFlag()).setStatus(device.getStatus())
                .setVersion(device.getVersion()).setLastPollTime(device.getLastPollTime())
                .setPrinterName(device.getPrinterName()).setPending(isPending(device))
                .setEnrollmentExpiresTime(isPending(device)
                        && device.getTokenCreatedTime() != null
                        ? device.getTokenCreatedTime().plusMinutes(ENROLLMENT_TTL_MINUTES) : null);
    }

    private static boolean isPending(TradeLogisticsPrintDeviceDO device) {
        return ACTIVE_ENROLLMENT_KEY.equals(device.getEnrollmentKey())
                || StrUtil.startWith(device.getDeviceCode(), "pending-");
    }

    private String mask(String value) {
        if (StrUtil.isBlank(value)) return null;
        return value.length() <= 4 ? "****" : "****" + StrUtil.subSuf(value, value.length() - 4);
    }

    private void validateHttpsEndpoint(String endpoint, String label) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw exception(LOGISTICS_SF_API_FAILED, label + "必须是 HTTPS 地址");
        }
    }

    private void validateOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            boolean allowedScheme = "https".equals(uri.getScheme())
                    || ("http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())));
            String expectedOrigin = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
            if (!allowedScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || !origin.equals(expectedOrigin)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw exception(LOGISTICS_CONFIGURATION_INVALID,
                    "PRINTBRIDGE_MERCHANT_ORIGIN 必须是标准 Origin，例如 https://merchant.example.com（不能带路径或尾部斜杠）");
        }
    }

    private void validatePrintBridgeConfiguration() {
        if (StrUtil.isBlank(printBridgeTaskEndpoint)) {
            throw exception(LOGISTICS_CONFIGURATION_INVALID, "缺少 PRINTBRIDGE_TASK_ENDPOINT");
        }
        if (StrUtil.isBlank(printBridgeMerchantOrigin)) {
            throw exception(LOGISTICS_CONFIGURATION_INVALID, "缺少 PRINTBRIDGE_MERCHANT_ORIGIN");
        }
        validateHttpsEndpoint(printBridgeTaskEndpoint, "PRINTBRIDGE_TASK_ENDPOINT");
        validateOrigin(printBridgeMerchantOrigin);
    }
}
