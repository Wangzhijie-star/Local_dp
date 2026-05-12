package com.hmdp.service.impl;

import com.hmdp.controller.BlogCommentsController;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    private final BlogCommentsController blogCommentsController;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Autowired
    private IBlogService blogService;
    @Autowired
    private FollowMapper followMapper;

    private static final String FEED_KEY_PREFIX = "feed:";

    BlogServiceImpl(BlogCommentsController blogCommentsController) {
        this.blogCommentsController = blogCommentsController;
    }
    
    @Override
    public Result getBlogById(Long id) {
        //查询这条blog，赋值用户信息
        Blog blog = baseMapper.selectById(id);
        if(blog == null){
            return Result.fail("blog is not exist");
        }
        Long userId=blog.getUserId();
        User user=userService.getById(userId);
        if(user==null){
            return Result.fail("user is not exist");
        }
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        //查询当前blog是否被当前用户点赞
        blog.setIsLike(isLiked(id));
        return Result.ok(blog);
    }

    @Override
    public Result getHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(isLiked(blog.getId()));
        });
        return Result.ok(records);
    }
    @Override
    public Result update(Long id){
        //确认是否点过赞了查询当前userId是否在blog的点赞集合中
        Long userId = UserHolder.getUser().getId();
        String key=RedisConstants.BLOG_LIKED_KEY+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score!=null){
            //取消点赞
            Boolean isSuccess=update().setSql("liked=liked-1").eq("id",id).update();
            if(isSuccess){
                //更新成功，删除点赞集合中的userId
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
            return Result.ok("cancel like success");
        }
        else{
            //点赞
            Boolean isSuccess=update().setSql("liked=liked+1").eq("id",id).update();
            if(isSuccess){
                //更新成功，添加点赞集合中的userId，用时间戳作为score
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }   
        }
        return Result.ok("like success");
    }
    @Override
    public Result queryBlogLikes(Long id) {
        String key=RedisConstants.BLOG_LIKED_KEY+id;
        Set<String> top5=stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5.isEmpty()||top5==null){
            return Result.ok(Collections.emptyList());
        }
        //解析其中的id
        List<Long> likeIds=top5.stream().map(Long::parseLong).collect(Collectors.toList());
        String likeIdsStr=StrUtil.join(",",likeIds);
        //查询点赞用户的用户信息
        List<UserDTO> usersDTOS=userService.query()
                        .in("id",likeIdsStr)
                        .last("ORDER BY FIELD(id,"+likeIdsStr+")")
                        .list()
                        .stream()
                        .map(user ->BeanUtil.copyProperties(user,UserDTO.class))
                        .collect(Collectors.toList());
        //user转成dto
        return Result.ok(usersDTOS);
    }
    //判断是否点赞过了
    private Boolean isLiked(Long id){
        Long userId = UserHolder.getUser().getId();
        String key=RedisConstants.BLOG_LIKED_KEY+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        return score!=null;
    }

    @Override
    public void pushBlogToFans(Long blogId, Long userId) {
        // 查询该用户的所有粉丝
        List<Long> fans = followMapper.selectFans(userId);
        if (fans == null || fans.isEmpty()) {
            return;
        }
        // 获取当前时间戳作为score
        long now = System.currentTimeMillis();
        // 将博客推送给每个粉丝的收件箱
        for (Long fanId : fans) {
            String key = FEED_KEY_PREFIX + fanId;
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), now);
        }
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 构建收件箱key
        String key = FEED_KEY_PREFIX + userId;
        // 查询收件箱中的blogId和score（时间戳）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 10);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(new ScrollResult());
        }
        // 解析数据，获取blogId和最小时间戳
        List<Long> blogIds = new ArrayList<>();
        long minTime = 0;
        int sameTimeCount = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            blogIds.add(Long.parseLong(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                sameTimeCount++;
            } else {
                minTime = time;
                sameTimeCount = 1;
            }
        }
        // 根据blogId查询博客信息
        String blogIdsStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query()
                .in("id", blogIdsStr)
                .last("ORDER BY FIELD(id," + blogIdsStr + ")")
                .list();
        // 补全用户信息和点赞状态
        blogs.forEach(blog -> {
            Long blogUserId = blog.getUserId();
            User user = userService.getById(blogUserId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(isLiked(blog.getId()));
        });
        // 构建返回结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(sameTimeCount);
        return Result.ok(scrollResult);
    }
}
