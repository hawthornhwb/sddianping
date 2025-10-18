package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.DeleteByMap;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public Result follow(Long followerId, Boolean isFollowed) {
        Long userId = UserHolder.getUser().getId();
        // 判断用户是否关注
        if (isFollowed) {
            // 如果用户未被关注，则关注
            Follow follow = new Follow();
            follow.setFollowUserId(followerId);
            follow.setUserId(userId);
            save(follow);
        } else {
            // 如果用户被关注，则取关
            remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", followerId)
                    .eq("user_id", userId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followerId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", followerId).eq("user_id", userId).count();
        log.info("count:{}", count);
        return Result.ok(count > 0);
    }
}
