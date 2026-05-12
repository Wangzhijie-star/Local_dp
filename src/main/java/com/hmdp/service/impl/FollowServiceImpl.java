package com.hmdp.service.impl;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import com.hmdp.dto.Result;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    private static final String FOLLOW_KEY_PREFIX = "follow:user:";

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY_PREFIX + userId;
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            baseMapper.insert(follow);
            // 将关注用户id添加到Redis的Set中
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            return Result.ok("success follow");
        }else{
            //取关
            followMapper.delete(userId, followUserId);
            // 从Redis的Set中移除关注用户id
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            return Result.ok("success cancel follow");
        }
    }

    @Override
    public Boolean isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Follow follow = followMapper.selectFollowInfom(userId, followUserId);
        return follow != null;
    }

    @Override
    public Result followCommons(Long targetUserId) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 构建两个用户的关注列表key
        String key1 = FOLLOW_KEY_PREFIX + userId;
        String key2 = FOLLOW_KEY_PREFIX + targetUserId;
        // 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 将交集转换为Long类型的用户id列表
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据id列表查询用户信息
        List<User> users = userMapper.selectBatchIds(ids);
        // 转换为UserDTO
        List<UserDTO> userDTOs = users.stream().map(user -> {
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setNickName(user.getNickName());
            dto.setIcon(user.getIcon());
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
