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
        // 2. 从Redis中获取用户信息 当时在login拦截器中改了这段代码的处理，这里忘记改了
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        String userStr = stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY + token);
        // 3. user信息不存在，拦截
//        log.info("userMap:{}", userMap);
//        if (userMap.isEmpty()) {
//            return true;
//        }
        if (StrUtil.isBlank(userStr)) {
            return true;
        }

        // 4. 保存用户信息到ThreadLocal中，工具类中的UserHolder已经写好了
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        UserHolder.saveUser(userDTO);

        UserDTO userDTO = JSONUtil.toBean(userStr, UserDTO.class);
        UserHolder.saveUser(userDTO);
        // 5. 刷新用户信息有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES); // 设置当前key的有效期为30分钟

        // 6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
