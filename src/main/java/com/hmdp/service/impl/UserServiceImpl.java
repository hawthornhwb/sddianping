package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
}
