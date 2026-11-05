package com.library.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import com.library.config.AlipayProperties;
import com.library.vo.BillRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 支付宝账单拉取与解析。
 * <p>
 * 账单接口返回的不是数据，而是一个 <b>zip 下载链接</b>，包里是一天的 CSV。
 * 两个容易踩的点：
 * <ol>
 *   <li>CSV 编码是 <b>GBK</b>，按 UTF-8 读会把商户名读成乱码；</li>
 *   <li>CSV 前面有若干行以 {@code #} 开头的说明，真正的表头在中间，
 *       而且列的顺序会随账单版本变化，<b>必须按列名定位而不是写死下标</b>。</li>
 * </ol>
 * 任何一步失败都返回 null 而不是抛异常：对账是后台任务，
 * 拉不到账单只意味着「今天没法对」，不该把定时任务打挂。
 */
@Slf4j
@Component
public class AlipayBillClient {

    private static final DateTimeFormatter BILL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayProperties props;

    /**
     * 拉取指定自然日的交易账单。
     *
     * @return 账单明细；模拟支付模式、无权限、网络失败时返回 null
     */
    public List<BillRow> fetch(LocalDate date) {
        if (props.isMockEnabled()) {
            // 模拟支付在支付宝侧根本没有交易，去拉账单只会得到「无数据」
            return null;
        }
        try {
            String url = requestDownloadUrl(date);
            if (url == null) {
                return null;
            }
            byte[] zipBytes = download(url);
            List<BillRow> rows = parseZip(zipBytes);
            // 这行日志是排查「对账为 0 差异」的关键：
            // 到底是「当天确实没有交易」，还是「文件下到了但没解析出来」。
            // 没有它，两种情况在页面上长得一模一样
            log.info("账单解析完成 date={} 明细 {} 笔", date, rows.size());
            return rows;
        } catch (Exception e) {
            log.error("拉取支付宝账单失败 date={}", date, e);
            return null;
        }
    }

    /**
     * 申请账单下载地址
     */
    private String requestDownloadUrl(LocalDate date) throws Exception {
        AlipayDataDataserviceBillDownloadurlQueryRequest request =
                new AlipayDataDataserviceBillDownloadurlQueryRequest();
        request.setBizContent("{\"bill_type\":\"trade\",\"bill_date\":\""
                + date.format(BILL_DATE) + "\"}");
        AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient.execute(request);
        if (!response.isSuccess()) {
            log.warn("账单下载地址申请失败 code={} msg={}", response.getSubCode(), response.getSubMsg());
            return null;
        }
        return response.getBillDownloadUrl();
    }

    private byte[] download(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 解析账单压缩包。
     * <p>
     * 一个 zip 里可能有多份 CSV：交易明细、账务明细汇总、退款明细。
     * 收款对账只要交易明细那一份。判断「哪份是交易明细」有两个坑：
     * <ul>
     *   <li>文件名是中文的（{@code 20261102_支付宝交易明细(...).csv}），
     *       只找英文 {@code trade} 会一个都匹配不上，所以同时认「交易」；</li>
     *   <li>汇总表也可能排在前面且同样以 .csv 结尾，解析出来是空表。
     *       所以解析出空表时要<b>继续看下一个文件</b>，而不是直接返回空。</li>
     * </ul>
     */
    List<BillRow> parseZip(byte[] zipBytes) throws IOException {
        // 必须显式指定 GBK 读取条目名。支付宝账单包是在中文 Windows 环境打的，
        // 条目名按 GBK 编码，而 ZipInputStream 默认用 UTF-8 解码，
        // 遇到中文文件名会直接抛 IllegalArgumentException: malformed input。
        // 注意：这是「条目名」编码，CSV 内容另有自己的 GBK 解码（见 parseCsv）
        try (ZipInputStream zis = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes), Charset.forName("GBK"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                // 打出实际文件名：支付宝的命名规则变过好几次，出问题时这是唯一的线索
                log.info("账单包条目: {}", entryName);
                String name = entryName.toLowerCase();
                if (!name.endsWith(".csv")) {
                    continue;
                }
                // 汇总表没有逐笔订单号，解析必然是空表。
                // 注意中文名是「业务明细(汇总)」，不含 summary，必须单独判「汇总」
                if (name.contains("summary") || name.contains("汇总")) {
                    log.info("跳过汇总文件: {}", entryName);
                    continue;
                }
                List<BillRow> rows = parseCsv(zis);
                log.info("解析文件 {} 得到 {} 笔", entryName, rows.size());
                if (!rows.isEmpty()) {
                    return rows;
                }
                // 空表说明这份不是明细（或只有表头），继续看下一个
            }
        }
        return new ArrayList<>();
    }

    /**
     * 解析单个 CSV 流：定位表头 → 按列名取「商户订单号」「订单金额」「交易号」。
     */
    private List<BillRow> parseCsv(InputStream in) throws IOException {
        List<BillRow> rows = new ArrayList<>();
        // 刻意不用 try-with-resources：BufferedReader 关闭时会连带关闭底层流，
        // 而这里的底层流是 ZipInputStream——关掉之后外层 getNextEntry() 会抛
        // IOException: Stream closed，整个账单解析直接失败、降级成模拟对账。
        // 流的生命周期由 parseZip 的 try-with-resources 统一管理，这里只读不关。
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName("GBK")));
        {
            String line;
            int idxOutTradeNo = -1;
            int idxAmount = -1;
            int idxTradeNo = -1;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                List<String> cells = splitCsv(line);
                if (idxOutTradeNo < 0) {
                    // 第一行非注释行就是表头
                    idxOutTradeNo = indexOf(cells, "商户订单号");
                    idxAmount = indexOf(cells, "订单金额");
                    idxTradeNo = indexOf(cells, "交易号");
                    if (idxOutTradeNo < 0 || idxAmount < 0) {
                        log.warn("账单表头无法识别，放弃解析: {}", cells);
                        return rows;
                    }
                    continue;
                }                if (cells.size() <= Math.max(idxOutTradeNo, idxAmount)) {
                    continue;
                }
                String outTradeNo = cells.get(idxOutTradeNo).trim();
                // 金额可能带千分位（1,234.00）或前后空格，先清干净再转 BigDecimal
                String amountText = cells.get(idxAmount).trim().replace(",", "").replace(" ", "");
                if (outTradeNo.isEmpty() || amountText.isEmpty()) {
                    continue;
                }
                BillRow row = new BillRow();
                row.setOutTradeNo(outTradeNo);
                row.setAmount(new BigDecimal(amountText));
                if (idxTradeNo >= 0 && cells.size() > idxTradeNo) {
                    row.setTradeNo(cells.get(idxTradeNo).trim());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 按列名定位。用「包含」而不是「精确等于」，因为支付宝账单的列名带单位后缀：
     * 实际表头是 {@code 支付宝交易号}、{@code 订单金额（元）}、{@code 商家实收（元）}，
     * 写死「交易号」「订单金额」会一个都匹配不上，解析直接返回空表——
     * 而空表又会让所有本地单被判成「渠道无此交易」。
     * <p>
     * 支付宝的列顺序随账单版本变过好几次，只能按名找，不能按下标。
     */
    private int indexOf(List<String> cells, String name) {
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i).trim();
            if (cell.contains(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 拆分一行 CSV，支持双引号包裹（引号内的逗号不拆）。
     */
    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString());
        return cells;
    }
}
