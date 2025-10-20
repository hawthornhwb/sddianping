package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.DeleteByMap;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceImpl userService;


    @Override
    public Result follow(Long followerId, Boolean isFollowed) {
        Long userId = UserHolder.getUser().getId();
        // 判断用户是否关注
        if (isFollowed) {
            // 如果用户未被关注，则关注
            Follow follow = new Follow();
            follow.setFollowUserId(followerId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess == false) {
                return Result.ok();
            }
            stringRedisTemplate.opsForSet().add(userId.toString(), followerId.toString());
        } else {
            // 如果用户被关注，则取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", followerId)
                    .eq("user_id", userId));
            if (isSuccess == false) {
                return Result.ok();
            }
            stringRedisTemplate.opsForSet().remove(followerId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followerId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", followerId).eq("user_id", userId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommon(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userId.toString(), id.toString());
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> intersectList = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<User> users = userService.listByIds(intersectList);
        List<UserDTO> userDTOs = users.stream()
                .map(User -> BeanUtil.copyProperties(User, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }
}
