package com.cjj.controller;

import com.cjj.dto.LoginFormDTO;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.User;
import com.cjj.entity.UserInfo;
import com.cjj.service.IFollowService;
import com.cjj.service.ISignService;
import com.cjj.service.IUserInfoService;
import com.cjj.service.IUserService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.cjj.utils.RedisConstants.LOGIN_USER_KEY;

import static com.cjj.utils.RedisConstants.FANS_KEY;
import static com.cjj.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 陈俊杰
 * @since 2026-6-3
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IFollowService followService;

    @Resource
    private ISignService signService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path getUploadPath() {
        Path p = Paths.get(uploadDir, "avatars");
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(uploadDir).resolve("avatars");
        }
        return p;
    }

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 退出登录（清除 Redis Token）
     */
    @PostMapping("/logout")
    public Result doLogout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && !token.isEmpty()) {
            stringRedisTemplate.delete(com.cjj.utils.RedisConstants.LOGIN_USER_KEY + token);
        }
        UserHolder.removeUser();
        return Result.ok("已退出");
    }

    /**
     * 用户统计数据（关注数、粉丝数、获赞数）
     */
    @GetMapping("/statistics")
    public Result statistics() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();

        // 关注数（Redis Set SCARD）
        Long followCount = stringRedisTemplate.opsForSet().size(FOLLOW_KEY + userId);
        // 粉丝数（Redis Set SCARD）
        Long fansCount = stringRedisTemplate.opsForSet().size(FANS_KEY + userId);
        // 获赞数（TODO: 从探店笔记聚合，暂无则返回 0）
        Long likeCount = 0L;

        Map<String, Long> data = new HashMap<>();
        data.put("followCount", followCount == null ? 0 : followCount);
        data.put("fansCount", fansCount == null ? 0 : fansCount);
        data.put("likeCount", likeCount);
        return Result.ok(data);
    }

    /**
     * 签到（Redis BitMap）
     * Key: sign:{userId}:{yyyyMM}
     * offset: 当月第几天 - 1
     */
    @PostMapping("/sign")
    public Result sign() {
        return signService.sign();
    }

    /**
     * 查询连续签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return signService.signCount();
    }

    /**
     * 签到日历
     */
    @GetMapping("/sign/calendar")
    public Result signCalendar() {
        return signService.signCalendar();
    }

    /**
     * 更新个人信息（昵称、性别、生日、所在地、简介）
     */
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody Map<String, String> body,
                                 javax.servlet.http.HttpServletRequest request) {
        UserDTO me = UserHolder.getUser();
        if (me == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = me.getId();
        String token = request.getHeader("Authorization");

        // 1. 更新 User 表（昵称）+ 同步 Redis
        if (body.containsKey("nickName")) {
            User user = userService.getById(userId);
            if (user != null) {
                user.setNickName(body.get("nickName"));
                userService.updateById(user);
                // 同步 Redis Session
                if (token != null && !token.isEmpty()) {
                    stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "nickName", body.get("nickName"));
                }
            }
        }

        // 2. 更新 UserInfo 表（所在地、性别、生日、简介）
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            info = new UserInfo();
            info.setUserId(userId);
        }
        if (body.containsKey("city")) info.setCity(body.get("city"));
        if (body.containsKey("gender")) {
            String g = body.get("gender");
            if (g != null && !g.isEmpty()) info.setGender("1".equals(g) || "女".equals(g));
            else info.setGender(null);
        }
        if (body.containsKey("birthday")) {
            String b = body.get("birthday");
            if (b != null && !b.isEmpty()) info.setBirthday(java.time.LocalDate.parse(b));
            else info.setBirthday(null);
        }
        if (body.containsKey("introduce")) info.setIntroduce(body.get("introduce"));
        userInfoService.saveOrUpdate(info);

        return Result.ok("更新成功");
    }

    /**
     * 上传头像
     * POST /api/user/avatar  (multipart/form-data, field: file)
     * 保存图片 → 更新 User.icon → 同步 Redis Session
     */
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file,
                                javax.servlet.http.HttpServletRequest request) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        Long userId = me.getId();

        if (file.isEmpty()) return Result.fail("请选择图片");
        if (file.getSize() > 2 * 1024 * 1024) return Result.fail("头像不能超过 2MB");

        try {
            // 保存到 uploads/avatars/
            Path dir = getUploadPath();
            Files.createDirectories(dir);
            String ext = ".jpg";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }
            String fileName = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path filePath = dir.resolve(fileName);
            file.transferTo(filePath.toFile());

            String url = "/uploads/avatars/" + fileName;

            // 更新 User 表
            User user = userService.getById(userId);
            if (user != null) {
                user.setIcon(url);
                userService.updateById(user);
            }

            // 同步 Redis Session
            String token = request.getHeader("Authorization");
            if (token != null && !token.isEmpty()) {
                stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "icon", url);
            }

            log.info("city-review 头像更新成功 → userId={}, url={}", userId, url);
            return Result.ok(url);
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return Result.fail("上传失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
