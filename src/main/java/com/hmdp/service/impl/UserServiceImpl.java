package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        if (phoneInvalid) {
            // 1.1 手机号不符合，则返回错误信息
            return Result.fail("sendCode: 手机号格式错误");
        }
        // 2. 生成验证码 用code符号记录
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码到 session 注：这一步的目的是为了后续校验
        session.setAttribute("code", code);
        // 4. todo 发送验证码 暂时用日志进行记录，后续可以用阿里云服务器实现。
        log.info("打印验证码:{}", code);
        // 5. 返回正确信息
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号 注：每次请求都是独立的请求，因而需要再次校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 手机号不符合，则返回错误信息
            return Result.fail("login: 手机号格式错误");
        }
        // 2. 校验验证码
        String code = loginForm.getCode();
        if (code == null || !session.getAttribute("code").toString().equals(code)) {
            return Result.fail("login: 验证码错误或不存在");
        }
        // 3. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4. 用户不存在
        if (user == null) {
            // 4.1 创建新用户，将用户信息保存到数据库中
            user = createUserWithPhone(phone);
        }

        // 5. 将用户信息保存到session中
        session.setAttribute("user", user);

        return Result.ok(); // 基于session不需要返回登录凭证
    }

    private User createUserWithPhone(String phone) {
        // 4.1 创建新用户，将用户信息保存到数据库中
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
