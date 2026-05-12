package com.hmdp.controller_jmeter_test;

import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.dto.UserDTO;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试专用登录接口
 * 用于批量获取token进行压力测试
 */
@RestController
@RequestMapping("/test")
public class TestLoginController {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试专用登录 - 免验证码
     * 仅用于测试环境！
     */
    @PostMapping("/login/{phone}")
    public Result testLogin(@PathVariable String phone) {
        // 查询用户
        User user = userService.query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 生成token
        String token = generateToken(user);
        return Result.ok(token);
    }

    /**
     * 批量获取token - 返回所有用户的token
     * 仅用于测试环境！
     */
    @GetMapping("/batch-login")
    public Result batchLogin() {
        // 从数据库获取所有用户
        List<User> users = userService.list();
        
        StringBuilder sb = new StringBuilder();
        sb.append("phone,token\n");

        for (User user : users) {
            // 生成token
            String token = generateToken(user);
            sb.append(user.getPhone()).append(",").append(token).append("\n");
        }

        // 输出到控制台，你可以复制保存为csv文件
        System.out.println("========== TOKEN LIST ==========");
        System.out.println(sb.toString());
        System.out.println("================================");

        return Result.ok("批量登录完成，请查看控制台输出");
    }

    /**
     * 生成token并保存到Redis
     */
    private String generateToken(User user) {
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user, userDTO);
        
        // 使用CopyOptions设置忽略null值，并转换为String类型
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return null;
                            }
                            return fieldValue.toString();
                        }));
        
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, map);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 
                RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        
        return token;
    }
}
