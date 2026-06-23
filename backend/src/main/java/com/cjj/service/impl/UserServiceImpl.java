package com.cjj.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.LoginFormDTO;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.User;
import com.cjj.mapper.UserMapper;
import com.cjj.service.IUserService;
import com.cjj.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.cjj.utils.RedisConstants.*;
import static com.cjj.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * city-review 用户服务实现
 * 验证码存 Redis String，用户 Session 存 Redis Hash
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误,请重新输入");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("===== city-review 登录验证码为：{} =====", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误,请重新输入");
        }

        // 1. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        String code = loginForm.getCode();

        if (cacheCode == null) {
            return Result.fail("请先获取验证码");
        }
        if (code == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 2. 查询或创建用户
        User user;
        try {
            // 直接用 Wrappers 构建查询条件，避免使用 ServiceImpl.query() 链式调用
            user = getBaseMapper().selectOne(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, loginForm.getPhone()));
        } catch (Exception e) {
            log.error("city-review 数据库查询失败", e);
            return Result.fail("系统异常，请稍后再试");
        }

        if (user == null) {
            try {
                user = createUserWithPhone(loginForm.getPhone());
            } catch (Exception e) {
                log.error("city-review 创建用户失败", e);
                return Result.fail("注册失败，请稍后再试");
            }
        }

        // 3. 生成 Token 并存入 Redis Hash（分布式 Session）
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        log.info("city-review 登录成功 → userId={}, phone={}", user.getId(), user.getPhone());
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
