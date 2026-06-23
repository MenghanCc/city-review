package com.cjj.config;

import com.cjj.utils.LoginInterceptor;
import com.cjj.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.io.File;

/**
 * city-review MVC 配置
 * - 注册 Token 刷新拦截器（优先级最高）
 * - 注册登录拦截器（排除公开接口）
 * - SPA 路由回退（非 /api 请求转发到 index.html）
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 映射 /uploads/** 到本地目录，使上传的图片可直接通过 URL 访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir.getAbsolutePath() + "/");
    }

    /**
     * 注册拦截器
     * LoginInterceptor 只拦截 /api/** 路径，前端静态资源不受影响
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token 刷新拦截器（order=0，最先执行）：刷新 Redis 中用户 Token 的 TTL
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/api/**")
                .order(0);

        // 登录拦截器（order=1）：只拦截 /api/**，排除公开接口
        // 前端静态资源（/, /index.html, /assets/** 等）不在拦截范围内
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/api/**")       // 只拦截 API 请求
                .excludePathPatterns(
                        "/api/user/code",          // 发送验证码
                        "/api/user/login",         // 登录
                        "/api/shop/**",            // 商户查询（公开）
                        "/api/shops/**",           // 附近商户（公开）
                        "/api/shop-type/**",       // 商户类型（公开）
                        "/api/voucher/list/**",    // 优惠券列表（公开）
                        "/api/blog/hot",           // 热门笔记（公开）
                        "/api/comments/blog/**",   // 评论列表（公开）
                        "/api/upload/**"           // 图片访问（公开）
                ).order(1);
    }

    /**
     * Spring Boot 自动将 static/index.html 作为欢迎页，
     * Vue Router 使用 Hash 模式（/#/path），无需额外配置。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 不注册任何 viewController，由 Spring Boot 默认机制处理静态资源
    }
}
