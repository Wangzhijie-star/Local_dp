package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import cn.hutool.json.JSONUtil;



/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 王智杰
 * @since 2026-04-20
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final String Snick_name_head="User_";
    @Override
    public String sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return "手机号格式错误！";
        }
        // 2.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        String jsonCode = JSONUtil.toJsonStr(code);
        // 3.保存验证码到 redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone, jsonCode, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return "sucess";
    }
    @Override
    public String login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合，返回错误信息
            return "手机号格式错误！";
        }
        // 2.校验验证码
        Object cacheCode = (String) stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        log.info("用户输入的验证码: {}", code);
        if(cacheCode == null || !cacheCode.toString().equals(code)){
             //4.不一致，报错
            return "验证码错误";
        }
        //5.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //6.判断用户是否存在
        if(user == null){
            //不存在，则创建
            user =  createUserWithPhone(phone);
        }
        //6.将用户对象转换为UserDTO对象,并转换为map
        UserDTO userDTO=new UserDTO();
        BeanUtil.copyProperties(user, userDTO);
        Map<String,Object> map = BeanUtil.beanToMap(
                userDTO, 
                new java.util.HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                        fieldValue.toString())
        );
        //生成随机字符串作为token，将user对象作为redis中的hash存储，存储到session中
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token, map);
        log.info("success login,token:{}", token);
        //设置过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }
    //根据手机号创建用户
    private User createUserWithPhone(String phone){
        User user=new User();
        //设置用户名生成随机字符串
        String Snick_name=Snick_name_head+RandomUtil.randomNumbers(10);
        user.setNickName(Snick_name);
        //设置电话
        user.setPhone(phone);
        //保存用户用mybatis-plus
        save(user);
        //返回用户对象
        return user;
    }
    
}

