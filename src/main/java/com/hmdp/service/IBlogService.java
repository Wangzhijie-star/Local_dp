package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    public Result getBlogById(Long id);

    public Result getHotBlog(Integer current);

    public Result update(Long id);

    public Result queryBlogLikes(Long id);

    /**
     * 将博客推送给粉丝的收件箱
     * @param blogId 博客ID
     * @param userId 发布博客的用户ID
     */
    void pushBlogToFans(Long blogId, Long userId);

    /**
     * 滚动查询关注用户的博客
     * @param max 最大时间戳（上次查询的最小时间戳）
     * @param offset 偏移量
     * @return 滚动查询结果
     */
    Result queryBlogOfFollow(Long max, Integer offset);

}
