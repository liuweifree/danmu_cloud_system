package cn.partytime.clientHandler;

import cn.partytime.config.DanmuChannelRepository;
import cn.partytime.service.ClientCacheService;
import cn.partytime.service.DanmuSendService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Created by lENOVO on 2016/10/9.
 */

@Slf4j
@Component
public class RealTimeDanmuHandler {


    @Resource(name = "threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private DanmuSendService danmuSendService;

    public void danmuListenHandler(String addressId) {
        log.info("实时弹幕监听线程启动");
        try {
            threadPoolTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    danmuSendService.sendDanmu(addressId);
                }
            });
        }catch (Exception e) {
            log.error("实时弹幕监听线程异常:{}", e.getMessage());
        }
    }

}
