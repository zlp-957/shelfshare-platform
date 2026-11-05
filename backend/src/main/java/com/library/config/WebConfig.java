package com.library.config;

import com.library.common.AdminInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web MVC 配置：跨域、拦截器、静态资源映射
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Value("${library.file.upload-dir}")
    private String uploadDir;

    @Value("${library.file.url-prefix}")
    private String urlPrefix;

    /**
     * 允许跨域的来源白名单（逗号分隔）。不再反射任意 Origin，避免任意站点携带凭证访问接口。
     * 可通过 application.yml 的 library.cors.allowed-origins 覆盖。
     */
    @Value("${library.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 密码找回的两个接口必须放行：用户正是登不进去才会用到它们
        String[] publicPaths = {
                "/auth/login",
                "/auth/register",
                "/password-reset/apply",
                "/password-reset/reset",
                "/uploads/**",
                "/pay/notify/**",
                "/error"
        };
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(publicPaths);
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(publicPaths);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // upload-dir 是相对路径时，基准是「启动时的工作目录」，不是 jar 所在目录。
        // 用 start-backend.bat 启动时工作目录就是项目根，一切正常；
        // 但如果 cd 到 backend 再 java -jar，路径会变成 backend/uploads（不存在），
        // 结果是所有封面 404，而接口本身照样返回 200——排查时极难联想到工作目录。
        // 所以这里把实际解析出的绝对路径打出来，不存在时直接告警。
        File dir = new File(uploadDir);
        String absolutePath = dir.getAbsolutePath();
        if (!dir.exists()) {
            log.warn("封面目录不存在: {}。{} 下的请求会全部 404。"
                            + "相对路径的基准是启动时的工作目录——请在项目根目录启动，"
                            + "或把 library.file.upload-dir 配成绝对路径",
                    absolutePath, urlPrefix);
        } else {
            log.info("封面目录: {} -> {}", urlPrefix, absolutePath);
        }
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
