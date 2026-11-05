package com.library.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.common.BusinessException;
import com.library.entity.IsbnCache;
import com.library.mapper.IsbnCacheMapper;
import com.library.vo.IsbnBookVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * ISBN 元数据查询。
 * <p>
 * 管理端录入新书时手打书名、作者、出版社既慢又容易错，扫码枪扫 ISBN 后
 * 自动回填是最高频的提效点。
 * <p>
 * 三个必须处理好的工程问题：
 * <ol>
 *   <li><b>校验位</b>：ISBN-13 最后一位是校验位，扫错一位是常态。
 *       本地先算一遍，错误的码根本不用发出去，省一次外网往返还能给出准确提示。</li>
 *   <li><b>缓存</b>：采购时一箱书连扫几十本，同一本书可能被扫多次；
 *       上游接口挂了，已查过的书仍然能录。</li>
 *   <li><b>降级</b>：查询失败不阻断录入，只提示「未查到，请手动填写」。
 *       第三方接口不可用不能变成「书录不进去」。</li>
 * </ol>
 */
@Slf4j
@Service
public class IsbnLookupService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private IsbnCacheMapper isbnCacheMapper;

    /** 第三方图书元数据接口。默认 OpenLibrary（免费、无需 key） */
    @Value("${library.isbn.api-url:https://openlibrary.org/api/books}")
    private String apiUrl;

    @Value("${library.isbn.timeout-ms:3000}")
    private int timeoutMs;

    @Value("${library.isbn.enabled:true}")
    private boolean enabled;

    /**
     * 按 ISBN 查询图书信息
     *
     * @param rawIsbn 用户输入或扫码得到的 ISBN（可带连字符、空格）
     */
    public IsbnBookVO lookup(String rawIsbn) {
        String isbn = normalize(rawIsbn);
        validate(isbn);

        // 1) 本地缓存
        IsbnCache cached = isbnCacheMapper.selectById(isbn);
        if (cached != null && StringUtils.hasText(cached.getTitle())) {
            return toVO(cached, "cache");
        }

        if (!enabled) {
            throw new BusinessException("ISBN 自动查询未启用，请手动填写图书信息");
        }

        // 2) 第三方接口
        IsbnBookVO remote = fetchRemote(isbn);
        if (remote == null || !StringUtils.hasText(remote.getTitle())) {
            throw new BusinessException("未查询到该 ISBN 的图书信息，请手动填写");
        }
        saveCache(isbn, remote);
        remote.setSource("remote");
        return remote;
    }

    /**
     * 规整 ISBN：去掉连字符和空格，X 统一成大写
     */
    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("请输入 ISBN");
        }
        String s = raw.replaceAll("[-\\s]", "").toUpperCase();
        if (s.length() != 10 && s.length() != 13) {
            throw new BusinessException("ISBN 应为 10 位或 13 位，请检查是否扫全");
        }
        return s;
    }

    /**
     * 校验位验证。扫错一位时能立刻发现，不用等接口返回空。
     */
    private void validate(String isbn) {
        if (isbn.length() == 13) {
            if (!isbn.matches("\\d{13}")) {
                throw new BusinessException("ISBN-13 只能是数字");
            }
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                sum += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            }
            int check = (10 - sum % 10) % 10;
            if (check != isbn.charAt(12) - '0') {
                throw new BusinessException("ISBN 校验位不正确，可能扫错了一位，请重新扫描");
            }
        } else {
            if (!isbn.matches("\\d{9}[\\dX]")) {
                throw new BusinessException("ISBN-10 格式不正确");
            }
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += (isbn.charAt(i) - '0') * (10 - i);
            }
            sum += (isbn.charAt(9) == 'X' ? 10 : isbn.charAt(9) - '0');
            if (sum % 11 != 0) {
                throw new BusinessException("ISBN 校验位不正确，可能扫错了一位，请重新扫描");
            }
        }
    }

    /**
     * 调第三方接口。任何异常都返回 null，由上层转成「请手动填写」。
     */
    private IsbnBookVO fetchRemote(String isbn) {
        HttpURLConnection conn = null;
        try {
            String url = apiUrl + "?bibkeys=ISBN:" + URLEncoder.encode(isbn, "UTF-8")
                    + "&format=json&jscmd=data";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("ISBN 查询接口返回 {} isbn={}", code, isbn);
                return null;
            }
            String body = readAll(conn.getInputStream());
            return parse(isbn, body);
        } catch (Exception e) {
            // 上游超时/断网/返回脏数据都不该阻断录入
            log.warn("ISBN 查询失败 isbn={} err={}", isbn, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 解析 OpenLibrary 响应。
     * <p>
     * 返回结构形如：
     * <pre>
     * {"ISBN:9780140328721": {"title": "...", "authors": [{"name": "..."}],
     *  "publishers": [{"name": "..."}], "cover": {"medium": "..."}}}
     * </pre>
     * 查不到时返回空对象 {@code {}}。
     */
    private IsbnBookVO parse(String isbn, String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode node = root.get("ISBN:" + isbn);
            if (node == null || node.isNull()) {
                return null;
            }
            IsbnBookVO vo = new IsbnBookVO();
            vo.setIsbn(isbn);
            vo.setTitle(text(node, "title"));
            vo.setAuthor(firstName(node.get("authors")));
            vo.setPublisher(firstName(node.get("publishers")));

            JsonNode cover = node.get("cover");
            if (cover != null && cover.isObject()) {
                String medium = text(cover, "medium");
                vo.setCover(StringUtils.hasText(medium) ? medium : text(cover, "small"));
            }

            StringBuilder extra = new StringBuilder();
            String publishDate = text(node, "publish_date");
            if (StringUtils.hasText(publishDate)) {
                extra.append("出版日期：").append(publishDate);
            }
            String pages = text(node, "number_of_pages");
            if (StringUtils.hasText(pages)) {
                extra.append(extra.length() > 0 ? "；" : "").append("页数：").append(pages);
            }
            vo.setExtra(extra.toString());
            return vo;
        } catch (Exception e) {
            log.warn("ISBN 响应解析失败 isbn={}", isbn, e);
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 取数组第一项的 name，如 authors[0].name */
    private String firstName(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return null;
        }
        JsonNode first = array.get(0);
        return first == null ? null : text(first, "name");
    }

    private void saveCache(String isbn, IsbnBookVO vo) {
        IsbnCache cache = new IsbnCache();
        cache.setIsbn(isbn);
        cache.setTitle(vo.getTitle());
        cache.setAuthor(vo.getAuthor());
        cache.setPublisher(vo.getPublisher());
        cache.setCover(vo.getCover());
        cache.setRaw(vo.getExtra());
        cache.setCreateTime(LocalDateTime.now());
        try {
            isbnCacheMapper.insert(cache);
        } catch (DuplicateKeyException e) {
            // 并发查询同一本，别人先写了，忽略
        }
    }

    private IsbnBookVO toVO(IsbnCache cache, String source) {
        IsbnBookVO vo = new IsbnBookVO();
        vo.setIsbn(cache.getIsbn());
        vo.setTitle(cache.getTitle());
        vo.setAuthor(cache.getAuthor());
        vo.setPublisher(cache.getPublisher());
        vo.setCover(cache.getCover());
        vo.setExtra(cache.getRaw());
        vo.setSource(source);
        return vo;
    }
}
