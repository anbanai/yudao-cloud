package cn.iocoder.yudao.module.pay.framework.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class PayJobConfiguration {

    public static final String NOTIFY_THREAD_POOL_TASK_EXECUTOR = "NOTIFY_THREAD_POOL_TASK_EXECUTOR";
    public static final String YEEPAY_EVENT_THREAD_POOL_TASK_EXECUTOR = "YEEPAY_EVENT_THREAD_POOL_TASK_EXECUTOR";

    @Bean(NOTIFY_THREAD_POOL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor notifyThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8); // 设置核心线程数
        executor.setMaxPoolSize(16); // 设置最大线程数
        executor.setKeepAliveSeconds(60); // 设置空闲时间
        executor.setQueueCapacity(100); // 设置队列大小
        executor.setThreadNamePrefix("notify-task-"); // 配置线程池的前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 进行加载
        executor.initialize();
        return executor;
    }

    @Bean(YEEPAY_EVENT_THREAD_POOL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor yeepayEventThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("yeepay-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

}
