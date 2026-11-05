package com.library.util;

import com.library.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传工具类（图书封面）
 */
@Component
public class FileUtil {

    @Value("${library.file.upload-dir}")
    private String uploadDir;

    @Value("${library.file.url-prefix}")
    private String urlPrefix;

    /** 允许的图片类型 */
    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png");

    /** 单张封面最大 2MB */
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    /**
     * 保存封面图片，返回访问 URL
     */
    public String saveCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        // 大小校验
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("封面图片大小不能超过 2MB");
        }
        // 后缀校验
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("只允许上传 jpg / png 格式图片");
        }
        // 真实内容校验：仅看后缀可被"文本改名 .jpg"绕过，这里读取文件头并实际解码
        String realExt = detectRealImageType(file);
        if (realExt == null) {
            throw new BusinessException("文件内容不是有效的图片，请上传真实的 jpg / png 图片");
        }
        // 生成唯一文件名，扩展名以真实格式为准，防止伪装
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + realExt;
        // 确保目录存在
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("封面存储目录创建失败");
        }
        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest.getAbsoluteFile());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败，请稍后重试");
        }
        return urlPrefix + "/" + fileName;
    }

    /**
     * 通过文件魔数 + 实际解码双重校验，返回真实图片扩展名；非图片返回 null
     */
    private String detectRealImageType(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(head);
        } catch (IOException e) {
            return null;
        }
        if (read < 8) {
            return null;
        }
        String detected = null;
        boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A;
        if (jpeg) {
            detected = "jpg";
        } else if (png) {
            detected = "png";
        }
        if (detected == null) {
            return null;
        }
        // 进一步确认能真正解码为图片，拦截伪造文件头的内容
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return detected;
    }
}
