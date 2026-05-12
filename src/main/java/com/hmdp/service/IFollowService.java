package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
    /**
     * 关注或取关用户
     * @param followUserId 关注用户ID
     * @param isFollow 是否关注
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询用户是否关注了指定用户
     * @param followUserId 关注用户ID
     * @return 是否关注
     */
    Boolean isFollow(Long followUserId);

    /**
     * 查询与指定用户的共同关注列表
     * @param targetUserId 对方用户ID
     * @return 共同关注的用户列表
     */
    Result followCommons(Long targetUserId);

}
