package com.library.service;

import com.library.vo.BillRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝账单压缩包的解析测试。
 * <p>
 * 对应一个真实踩过的坑：账单 zip 的条目名是 GBK 编码（中文 Windows 环境打的包），
 * 而 {@code ZipInputStream} 默认按 UTF-8 解码，遇到中文文件名会抛
 * {@code IllegalArgumentException: malformed input}。
 * <p>
 * 这个异常被上层 catch 成「拉取账单失败」，然后降级为模拟对账——
 * 表面看像「应用没有账单权限」，实际是解析代码崩了。因为降级路径是静默的，
 * 不写测试几乎不可能发现：页面上一切正常，只是对账结果永远为 0 差异。
 */
class AlipayBillClientParseTest {

    private static final Charset GBK = Charset.forName("GBK");

    /**
     * 线上真实表头。
     * <p>
     * 注意两个坑：交易号叫「支付宝交易号」而不是「交易号」；
     * 金额叫「订单金额（元）」带单位后缀。写死不带后缀的名字会一个都匹配不上。
     */
    private static final String HEADER =
            "支付宝交易号,商户订单号,业务类型,商品名称,创建时间,完成时间,门店编号,门店名称,"
                    + "操作员,终端号,对方账户,订单金额（元）,商家实收（元）,支付宝红包（元）,"
                    + "集分宝（元）,支付宝优惠（元）,商家优惠（元）,券核销金额（元）,券名称,"
                    + "商家红包消费金额（元）,卡消费金额（元）,退款批次号/请求号,服务费（元）,分润（元）,备注";

    private static final String ROW =
            "2026110322001450001,PO20261103001001,交易,图书借阅押金,2026-11-03 10:00:00,"
                    + "2026-11-03 10:00:05,,,,,reader@example.com,20.00,20.00";

    @Test
    @DisplayName("中文（GBK）文件名的账单包应能正常解析，而不是抛 malformed input")
    void parsesZipWithChineseEntryName() throws Exception {
        // 文件名带中文 + trade，最贴近真实账单包
        byte[] zip = buildZip("20261103_支付宝交易明细_trade.csv", HEADER + "\n" + ROW);

        List<BillRow> rows = new AlipayBillClient().parseZip(zip);

        assertNotNull(rows, "解析结果不应为 null");
        assertEquals(1, rows.size(), "应解析出 1 条交易明细");
        BillRow row = rows.get(0);
        assertEquals("PO20261103001001", row.getOutTradeNo(), "商户订单号必须正确取出");
        assertEquals(0, new java.math.BigDecimal("20.00").compareTo(row.getAmount()), "金额应正确");
        assertEquals("2026110322001450001", row.getTradeNo(), "渠道交易号应正确取出");
    }

    @Test
    @DisplayName("中文文件名但不含 trade 关键字：退回第一个非汇总文件仍能解析")
    void fallsBackToFirstNonSummaryFile() throws Exception {
        // 文件名里没有 trade，第一轮扫描会跳过，第二轮兜底取第一个 CSV
        byte[] zip = buildZip("20261103_支付宝明细.csv", HEADER + "\n" + ROW);

        List<BillRow> rows = new AlipayBillClient().parseZip(zip);

        assertEquals(1, rows.size(), "兜底路径也应能解析");
    }

    @Test
    @DisplayName("zip 里同时有交易与退款明细时，优先取交易那份")
    void prefersTradeOverRefund() throws Exception {
        String refundHeader = "交易号,商户订单号,退款金额";
        String refundRow = "2026110322001450001,PO-REFUND,20.00";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, GBK)) {
            // 故意让退款明细排在前面，验证不会「取第一个就 break」
            zos.putNextEntry(new ZipEntry("20261103_退款明细_refund.csv"));
            zos.write((refundHeader + "\n" + refundRow).getBytes(GBK));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("20261103_交易明细_trade.csv"));
            zos.write((HEADER + "\n" + ROW).getBytes(GBK));
            zos.closeEntry();
        }

        List<BillRow> rows = new AlipayBillClient().parseZip(bos.toByteArray());

        assertEquals(1, rows.size());
        assertEquals("PO20261103001001", rows.get(0).getOutTradeNo(),
                "应取交易明细，而不是排在前面的退款明细");
    }

    @Test
    @DisplayName("空 zip / 无 CSV 时不抛异常，返回空列表")
    void emptyZipReturnsEmptyList() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, GBK)) {
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("nothing".getBytes(GBK));
            zos.closeEntry();
        }
        assertTrue(new AlipayBillClient().parseZip(bos.toByteArray()).isEmpty());
    }

    @Test
    @DisplayName("金额带千分位时也要能解析")
    void parsesAmountWithThousandsSeparator() throws Exception {
        String row = "2026110322001450001,PO-BIG,交易,图书,2026-11-03 10:00:00,2026-11-03 10:00:05,"
                + ",,,,reader@example.com,\"1,299.00\",\"1,299.00\"";
        byte[] zip = buildZip("20261103_trade.csv", HEADER + "\n" + row);

        List<BillRow> rows = new AlipayBillClient().parseZip(zip);

        assertEquals(1, rows.size());
        assertEquals(0, new java.math.BigDecimal("1299.00").compareTo(rows.get(0).getAmount()),
                "带千分位的金额必须清掉逗号再转数字");
    }

    private byte[] buildZip(String entryName, String content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, GBK)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(GBK));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
