package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;

import java.util.List;

public interface LogisticsManagementService {
    List<SfLogisticsAccountRespVO> getAccounts();
    Long saveAccount(SfLogisticsAccountSaveReqVO request);
    List<LogisticsPrintDeviceRespVO> getDevices();
    LogisticsPrintDeviceRespVO saveDevice(LogisticsPrintDeviceSaveReqVO request);
    LogisticsPrintDeviceRespVO enrollDevice();
    String createDiagnosticPayload(int paperWidthMm, int paperHeightMm);
}
