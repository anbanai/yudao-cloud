package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;

import java.util.List;

public interface LogisticsManagementService {
    List<SfLogisticsAccountRespVO> getAccounts();
    Long saveAccount(SfLogisticsAccountSaveReqVO request);
    List<LogisticsPrintDeviceRespVO> getDevices();
    LogisticsPrintDeviceRespVO saveDevice(Long merchantId, LogisticsPrintDeviceSaveReqVO request);
    List<LogisticsPrintDeviceRespVO> getMerchantDevices(Long merchantId);
    LogisticsPrintDeviceRespVO enrollDevice(Long merchantId);
    LogisticsPrintTaskRespVO createTestPrint(Long merchantId, Long deviceId, int paperWidthMm, int paperHeightMm);
}
