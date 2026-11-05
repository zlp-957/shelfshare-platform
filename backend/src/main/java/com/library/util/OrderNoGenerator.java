package com.library.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 统一订单号生成器。
 * <p>
 * 旧实现散落在 PayServiceImpl / PurchaseServiceImpl / CartServiceImpl 三处，
 * 都是「毫秒时间戳 + 单机 AtomicInteger 取模」：单机够用，一旦多实例部署，
 * 两个 JVM 各自从 0 开始自增，同一毫秒内必然撞号（pay_order 有唯一索引兜底，
 * 不会写脏数据，但用户会直接吃到 500）。
 * <p>
 * 这里按雪花算法的思路补上两个维度：
 * <pre>
 * 前缀(2) + 毫秒时间戳(17) + workerId(2) + 同毫秒序号(3)
 * </pre>
 * - workerId 由配置 {@code library.order.worker-id} 指定，部署几个实例就配几个不同值；
 * - 同毫秒序号用满 1000 后自旋等待下一毫秒，而不是取模回绕——回绕就会重复。
 * <p>
 * 仍然是「时间有序」的字符串，可以按单号直接排序，排查问题时比 UUID 友好。
 */
@Component
public class OrderNoGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrderNoGenerator.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 同一毫秒最多分配 1000 个序号 */
    private static final int MAX_SEQ = 999;

    /** 实例编号，0~99。多实例部署必须各不相同，否则退化成旧实现 */
    @Value("${library.order.worker-id:1}")
    private int workerId;

    private long lastMillis = -1L;

    private int seq = 0;

    /**
     * 启动时打印一次实例号。
     * <p>
     * 多实例部署忘了配 worker-id 是最容易发生的运维事故：号段重叠、唯一索引冲突，
     * 用户看到的是随机 500。日志里明确写出来，比事后翻代码快得多。
     */
    @PostConstruct
    public void checkWorkerId() {
        if (workerId < 0 || workerId > 99) {
            throw new IllegalStateException(
                    "library.order.worker-id 必须在 0~99 之间，当前=" + workerId);
        }
        log.info("订单号生成器已就绪 workerId={}（多实例部署必须保证各实例互不相同）", workerId);
    }

    /**
     * 生成订单号
     *
     * @param prefix 业务前缀，如 PO（支付单）/ PR（购买单）
     */
    public synchronized String next(String prefix) {
        long now = System.currentTimeMillis();
        if (now == lastMillis) {
            seq++;
            if (seq > MAX_SEQ) {
                // 同一毫秒序号用尽：等到下一毫秒，绝不回绕重复
                while (now <= lastMillis) {
                    now = System.currentTimeMillis();
                }
                seq = 0;
            }
        } else {
            seq = 0;
        }
        lastMillis = now;
        String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).format(TS);
        return prefix + time + String.format("%02d%03d", Math.abs(workerId) % 100, seq);
    }
}
