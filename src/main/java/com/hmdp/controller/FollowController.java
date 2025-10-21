package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("{id}/{isFollowed}")  // isFollowed的含义是该用户是否被关注，true: 该用户未被我关注；false: 该用户已被我关注
    public Result follow(@PathVariable("id") Long followerId, @PathVariable("isFollowed") Boolean isFollowed) {
        return followService.follow(followerId, isFollowed);
    }

    @GetMapping("or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followerId) {
        return followService.isFollow(followerId);
    }

    @GetMapping("common/{id}")
    public Result followCommon(@PathVariable("id") Long id) {
        return followService.followCommon(id);
    }
}
