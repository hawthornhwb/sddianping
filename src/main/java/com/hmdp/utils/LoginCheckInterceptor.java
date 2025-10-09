package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginCheckInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取session
        HttpSession session = request.getSession();
        // 2. 从session中获取user信息
        Object user = session.getAttribute("user");
        // 3. user信息不存在，则重定向到登录界面，并拦截
        if (user == null) {
//            response.sendRedirect("/login"); // 如果用户不存在，重定向到登录界面
            return false;
        }

        // 4. 保存用户信息到ThreadLocal中，工具类中的UserHolder已经写好了
        UserHolder.saveUser((UserDTO) user);

        // 5. 放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
