package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
public class LogisticsManagementServiceImpl implements LogisticsManagementService {

    @Resource private TradeLogisticsAccountMapper accountMapper;
    @Resource private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Resource private DeliveryExpressMapper expressMapper;
    @Resource private FileApi fileApi;

    @Override
    public List<SfLogisticsAccountRespVO> getAccounts() {
        return accountMapper.selectListAll().stream().map(this::toAccountResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccount(SfLogisticsAccountSaveReqVO request) {
        validateSfExpress(request.getLogisticsId());
        validateEndpoint(request.getEndpoint());
        TradeLogisticsAccountDO account = request.getId() == null ? new TradeLogisticsAccountDO()
                : accountMapper.selectById(request.getId());
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        if (request.getId() == null && (StrUtil.isBlank(request.getPartnerId())
                || StrUtil.isBlank(request.getCheckWord()) || StrUtil.isBlank(request.getMonthlyCard()))) {
            throw exception(LOGISTICS_SF_API_FAILED, "新建账号必须填写 Partner ID、校验码和月结卡号");
        }
        account.setName(request.getName()).setLogisticsId(request.getLogisticsId()).setEndpoint(request.getEndpoint())
                .setServiceCode(request.getServiceCode()).setTemplateCode(request.getTemplateCode())
                .setSenderName(request.getSenderName()).setSenderPhone(request.getSenderPhone())
                .setSenderProvince(request.getSenderProvince()).setSenderCity(request.getSenderCity())
                .setSenderDistrict(request.getSenderDistrict()).setSenderAddress(request.getSenderAddress())
                .setDefaultWeightKg(request.getDefaultWeightKg()).setPaperWidthMm(request.getPaperWidthMm())
                .setPaperHeightMm(request.getPaperHeightMm()).setDpi(request.getDpi())
                .setDefaultFlag(Boolean.TRUE.equals(request.getDefaultFlag())).setStatus(request.getStatus());
        if (StrUtil.isNotBlank(request.getPartnerId())) account.setPartnerId(request.getPartnerId());
        if (StrUtil.isNotBlank(request.getCheckWord())) account.setCheckWord(request.getCheckWord());
        if (StrUtil.isNotBlank(request.getMonthlyCard())) account.setMonthlyCard(request.getMonthlyCard());
        if (Boolean.TRUE.equals(account.getDefaultFlag())) accountMapper.clearDefault(account.getId());
        if (account.getId() == null) accountMapper.insert(account); else accountMapper.updateById(account);
        return account.getId();
    }

    @Override
    public List<LogisticsPrintDeviceRespVO> getDevices() {
        return deviceMapper.selectListAll().stream().map(device -> toDeviceResp(device, null)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsPrintDeviceRespVO saveDevice(LogisticsPrintDeviceSaveReqVO request) {
        TradeLogisticsPrintDeviceDO device = request.getId() == null ? new TradeLogisticsPrintDeviceDO()
                : deviceMapper.selectById(request.getId());
        if (device == null) throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        device.setDeviceCode(request.getDeviceCode()).setDeviceName(request.getDeviceName())
                .setDefaultFlag(Boolean.TRUE.equals(request.getDefaultFlag())).setStatus(request.getStatus());
        String token = null;
        if (device.getId() == null) {
            token = LogisticsTokenUtils.generate();
            device.setTokenHash(LogisticsTokenUtils.hash(token)).setTokenCreatedTime(LocalDateTime.now());
        }
        if (Boolean.TRUE.equals(device.getDefaultFlag())) deviceMapper.clearDefault(device.getId());
        if (device.getId() == null) deviceMapper.insert(device); else deviceMapper.updateById(device);
        return toDeviceResp(device, token);
    }

    @Override
    public LogisticsPrintDeviceRespVO rotateDeviceToken(Long id) {
        TradeLogisticsPrintDeviceDO device = deviceMapper.selectById(id);
        if (device == null) throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        String token = LogisticsTokenUtils.generate();
        deviceMapper.updateById(new TradeLogisticsPrintDeviceDO().setId(id)
                .setTokenHash(LogisticsTokenUtils.hash(token)).setTokenCreatedTime(LocalDateTime.now()));
        return toDeviceResp(device, token);
    }

    @Override
    @SneakyThrows
    public String createDiagnosticPayload() {
        BufferedImage image = new BufferedImage(799, 1199, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK); graphics.setStroke(new BasicStroke(4));
            graphics.drawRect(8, 8, image.getWidth() - 17, image.getHeight() - 17);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
            graphics.drawString("PrintBridge 100 x 150 mm", 70, 130);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 32));
            graphics.drawString("203 DPI / 799 x 1199 px", 90, 210);
            for (int y = 300; y < 1000; y += 100) graphics.drawLine(60, y, 740, y);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        String url = fileApi.createFile(output.toByteArray(), "printbridge-test-100x150.png",
                "trade/logistics/diagnostics", "image/png");
        return fileApi.presignGetUrl(url, 15 * 60).getCheckedData();
    }

    private SfLogisticsAccountRespVO toAccountResp(TradeLogisticsAccountDO account) {
        return new SfLogisticsAccountRespVO().setId(account.getId()).setName(account.getName())
                .setLogisticsId(account.getLogisticsId()).setEndpoint(account.getEndpoint())
                .setPartnerIdMasked(mask(account.getPartnerId())).setMonthlyCardMasked(mask(account.getMonthlyCard()))
                .setServiceCode(account.getServiceCode()).setTemplateCode(account.getTemplateCode())
                .setSenderName(account.getSenderName()).setSenderPhone(account.getSenderPhone())
                .setSenderProvince(account.getSenderProvince()).setSenderCity(account.getSenderCity())
                .setSenderDistrict(account.getSenderDistrict()).setSenderAddress(account.getSenderAddress())
                .setDefaultWeightKg(account.getDefaultWeightKg()).setPaperWidthMm(account.getPaperWidthMm())
                .setPaperHeightMm(account.getPaperHeightMm()).setDpi(account.getDpi())
                .setDefaultFlag(account.getDefaultFlag()).setStatus(account.getStatus());
    }

    private LogisticsPrintDeviceRespVO toDeviceResp(TradeLogisticsPrintDeviceDO device, String token) {
        return new LogisticsPrintDeviceRespVO().setId(device.getId()).setDeviceCode(device.getDeviceCode())
                .setDeviceName(device.getDeviceName()).setDefaultFlag(device.getDefaultFlag()).setStatus(device.getStatus())
                .setVersion(device.getVersion()).setLastPollTime(device.getLastPollTime()).setToken(token);
    }

    private String mask(String value) {
        if (StrUtil.isBlank(value)) return null;
        return value.length() <= 4 ? "****" : "****" + StrUtil.subSuf(value, value.length() - 4);
    }

    private void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("sf-express.com") || host.endsWith(".sf-express.com"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw exception(LOGISTICS_SF_API_FAILED, "API 地址必须是顺丰官方 sf-express.com HTTPS 地址");
        }
    }

    private void validateSfExpress(Long logisticsId) {
        DeliveryExpressDO express = expressMapper.selectById(logisticsId);
        if (express == null || !"SF".equalsIgnoreCase(express.getCode())) {
            throw exception(LOGISTICS_SF_API_FAILED, "快递公司必须选择顺丰（SF）");
        }
    }
}
