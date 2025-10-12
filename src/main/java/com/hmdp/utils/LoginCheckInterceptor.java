package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@Slf4j
@Component
public class LoginCheckInterceptor implements HandlerInterceptor {
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 从请求头中获取session
//       HttpSession session = request.getSession();
//        // 2. 从session中获取user信息
//        Object user = session.getAttribute("user");
//        // 3. user信息不存在，则重定向到登录界面，并拦截
//        if (user == null) {
//            response.sendRedirect("/login"); // 如果用户不存在，重定向到登录界面
//            return false;
//        }
//
//        // 4. 保存用户信息到ThreadLocal中，工具类中的UserHolder已经写好了
//        UserHolder.saveUser((UserDTO) user);
//
//        // 5. 放行
//        return true;
//    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取token
        String token = request.getHeader("authorization");
        // 2. 从Redis中获取用户信息
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        // 由于Login方法中已经将对象使用string存储了，因而这里也要修改
        String userStr = stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY + token);
        // 3. user信息不存在，则重定向到登录界面，并拦截
        if (StrUtil.isBlank(userStr)) {
            response.setStatus(401); // 如果用户不存在，重定向到登录界面
            return false;
        }

        // 4. 保存用户信息到ThreadLocal中，工具类中的UserHolder已经写好了
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
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
