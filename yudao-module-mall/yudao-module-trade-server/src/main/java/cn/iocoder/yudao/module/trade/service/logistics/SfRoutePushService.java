package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;

public interface SfRoutePushService {

    void process(SfRoutePushReqVO request);
}
