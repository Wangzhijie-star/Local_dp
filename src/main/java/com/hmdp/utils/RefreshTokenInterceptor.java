package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.lang.Nullable;
import cn.hutool.json.JSONUtil;



@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");

        log.info("token: {}", token);

        // 2. 判断 token 是否存在
        if (token == null || token.isEmpty()) {
            return true;
        }

        // 3. 拼接 redis key
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

        // 4. 获取 redis 中用户信息
        Map<Object, Object> userMap =stringRedisTemplate.opsForHash().entries(tokenKey);

        // 5. 判断用户是否存在
        if (userMap == null || userMap.isEmpty()) {
            return true;
        }

        // 6. 转换为 UserDTO
        UserDTO userDTO =
                JSONUtil.toBean(JSONUtil.toJsonStr(userMap), UserDTO.class);

        // 7. 保存用户到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 8. 刷新 token 有效期
        stringRedisTemplate.expire(
                tokenKey,
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                @Nullable Exception ex) throws Exception {
 
        // 移除 ThreadLocal
        UserHolder.removeUser();
    } 
}