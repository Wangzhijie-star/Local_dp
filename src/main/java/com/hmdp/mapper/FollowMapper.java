package com.hmdp.mapper;

import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {
    /**
     * 删除用户关注关系
     * @param userId 用户ID
     * @param followUserId 关注用户ID
     */
    @Delete("delete from tb_follow where user_id = #{userId} and follow_user_id = #{followUserId}")
    void delete(Long userId, Long followUserId);
    /**
     * 查询用户是否关注了指定用户
     * @param userId 用户ID
     * @param followUserId 关注用户ID
     * @return 是否关注
     */
    Follow selectFollowInfom(Long userId, Long followUserId);

    /**
     * 查询用户的粉丝列表
     * @param followUserId 被关注用户ID
     * @return 粉丝用户ID列表
     */
    @Select("select user_id from tb_follow where follow_user_id = #{followUserId}")
    List<Long> selectFans(Long followUserId);

}
