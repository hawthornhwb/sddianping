package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
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
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 获取博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在!");
        }
        isUserLiked(blog);
        // 获取用户信息
        queryUser(blog);

        return Result.ok(blog);
    }

    private void isUserLiked(Blog blog) {
        // 判断用户是否点过赞
        UserDTO user = UserHolder.getUser(); // 如果用户处于未登录状态，则始终显示未点赞状态
        if (user == null) {
            blog.setIsLike(false);
        } else {
            Long userId = user.getId();
            String key = "blog:isLiked:" + blog.getId();
            // 1. 判断用户是否点过赞 使用redis的set集合进行判断, key 为笔记id, value为用户id
            Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
            blog.setIsLike(score != null);
        }
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryUser(blog);
            // 判断用户是否点过赞
            isUserLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("当前用户未登录，请先登录!");
        }
        Long userId = user.getId();
        String key = "blog:isLiked:" + id;
        // 1. 判断用户是否点过赞 使用redis的set集合进行判断, key 为笔记id, value为用户id
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
//        log.info("isMember:{}",isMember);
        if (isMember == null) {
            // 1.1 如果用户未点赞，则1. 修改数据库； 2. 将用户增加至redis
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis()); // System.currentTimeMillis() 当前时间戳
            }
        } else {
            // 1.2 如果用户点过赞了，则1. 修改数据库； 2. 将用户从redis中去除
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikesById(Long id) {
        // 1. 从Redis中将点赞top查询出来
        String key = "blog:isLiked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);// 拿到的都是用户id
        if (top5 == null || top5.size() == 0) {
            return Result.ok(Collections.emptyList()); // 如果没有人点赞，返回空列表
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
//        List<UserDTO> userDTOS = userService.listByIds(ids)
//                .stream()
//                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOs = userService.query()
                .in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }


    private void queryUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
