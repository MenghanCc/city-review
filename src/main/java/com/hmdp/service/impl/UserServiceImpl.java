package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
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
 * @author 陈俊杰
 * @since 2026-01-01
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误,请重新输入");
        }
        // 如果不符合格式，返回错误信息

        // 如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到session
        session.setAttribute("code", code);
        // 发送验证码
        // TODO 实现发送验证码功能
        log.debug("发送验证码到手机号{}成功，验证码为{}", phone, code);


        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 校验手机号和验证码
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误,请重新输入");
        }

        Object cacheCode = session.getAttribute("code"); // session是存储任意类型对象
        String code = (String) loginForm.getCode();

        if(code == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }

        // 如果一致则根据手机号查询用户（使用MyBatisPlus）
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            // 如果用户不存在，创建一个用户。并且将手机号返回给变量user
            user = createUserWithPhone(loginForm.getPhone());
        }

        // 保存用户信息到session中(将user转为UserDTO类型，避免敏感信息被传到前端)
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user); // MP
        return user;
    }
}
