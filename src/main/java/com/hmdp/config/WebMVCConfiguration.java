package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebMVCConfiguration implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // token刷新拦截器
        registry.addInterceptor(
                        new RefreshTokenInterceptor(stringRedisTemplate)
                )
                .addPathPatterns("/**")
                .order(0);

        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(

                        // 用户相关
                        "/user/code",
                        "/user/login",
                        // 店铺相关
                        "/shop/**",
                        "/shop-type/**",
                        // 优惠券
                        "/voucher/**",
                        // 博客
                        "/blog/hot",
                        // 文件u
                        "/upload/**",
                        // 静态资源
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/imgs/**",
                        // 测试专用
                        "/test/**"
                )
                .order(1);
    }
}