package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;  // 第一个拦截器只负责刷新，不负责拦截
        }

        // 2. 判断用户是否存在
        String userStr = stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY + token);
        if (StrUtil.isBlank(userStr)) {
            return true;
        }

        // 3. 刷新用户登录状态
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES); // 设置当前key的有效期为30分钟

        // 4. 放行
        return true;
    }

}
