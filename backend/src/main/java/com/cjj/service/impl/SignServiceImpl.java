package com.cjj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Sign;
import com.cjj.mapper.SignMapper;
import com.cjj.service.ISignService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.cjj.utils.RedisConstants.USER_SIGN_KEY;
import static com.cjj.utils.RedisConstants.USER_SIGN_TTL;

/**
 * city-review 签到服务实现
 *
 * Redis BitMap 签到：
 *   Key:   sign:{userId}:{yyyyMM}
 *   Bit:   offset = 当月第几天 - 1（如 6月22日 → offset 21）
 *   命令：  SETBIT key offset 1
 *
 * 为什么用 BitMap 而不是 Set 或 MySQL？
 *   1. 极致内存：365天 ≈ 46字节/用户，Set 存365天需 ~7KB/用户
 *   2. 天然匹配：签到只有"已签/未签"两种状态（1 bit）
 *   3. BITFIELD 一条命令获取全月状态，无需遍历
 *   4. BITCOUNT 高效统计总天数
 *
 * 连续签到计算：
 *   1. BITFIELD GET u{本月天数} 0  → 获取全月位图（十进制）
 *   2. 对今天（含）之前的位，从右向左遍历连续的 1
 *   3. 遇到 0 停止，计数即连续天数
 */
@Slf4j
@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements ISignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 签到
     */
    @Override
    public Result sign() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();
        LocalDate now = LocalDate.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int offset = now.getDayOfMonth() - 1;  // offset 从 0 开始

        // SETBIT sign:{userId}:{yyyyMM} offset 1
        // 返回值为该位原来的值（0=今日首次签到，1=已签到）
        Boolean isSigned = stringRedisTemplate.opsForValue().setBit(key, offset, true);

        if (Boolean.TRUE.equals(isSigned)) {
            return Result.fail("今日已签到，请勿重复签到");
        }

        // 每次签到续期 1 年
        stringRedisTemplate.expire(key, USER_SIGN_TTL, TimeUnit.DAYS);

        log.info("city-review 签到成功 → userId={}, date={}, offset={}", userId, now, offset);
        return Result.ok("签到成功");
    }

    /**
     * 查询本月连续签到天数
     */
    @Override
    public Result signCount() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();
        LocalDate now = LocalDate.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();

        // BITFIELD sign:{userId}:{yyyyMM} GET u{本月天数} 0
        // 获取本月 1 日到今天的无符号整型位图
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        long bitField = result.get(0) == null ? 0 : result.get(0);
        // 从今天（末位）向月初方向遍历连续的 1
        int count = 0;
        while ((bitField & 1) == 1) {
            count++;
            bitField >>>= 1;
        }

        return Result.ok(count);
    }

    /**
     * 查询本月签到日历
     * 返回 List<Integer>，每个元素的值 0 或 1 代表当天是否签到
     */
    @Override
    public Result signCalendar() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();
        LocalDate now = LocalDate.now();
        YearMonth yearMonth = YearMonth.from(now);
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int daysInMonth = yearMonth.lengthOfMonth();

        // BITFIELD 获取全月签到位图
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(daysInMonth))
                        .valueAt(0)
        );

        long bitField = result == null || result.isEmpty() || result.get(0) == null
                ? 0 : result.get(0);

        // BITFIELD GET u{N} 0 返回的整数中：
        //   MSB（最高位）= Redis offset 0 = 第1天
        //   LSB（最低位）= Redis offset N-1 = 第N天
        // 因此需要从高位向低位依次读取，而非从 LSB 遍历
        List<Integer> calendar = new ArrayList<>(daysInMonth);
        for (int i = 1; i <= daysInMonth; i++) {
            int bitPos = daysInMonth - i;  // 第i天对应位图中的位置
            int signed = ((bitField >>> bitPos) & 1) == 1 ? 1 : 0;
            calendar.add(signed);
        }

        // 额外返回连续签到天数和本月签到总数供前端展示
        Map<String, Object> data = new HashMap<>();
        data.put("yearMonth", now.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        data.put("today", now.getDayOfMonth());
        data.put("calendar", calendar);

        // BITCOUNT 统计本月总签到天数（通过 execute 回调执行原生 BITCOUNT 命令）
        Long totalDays = stringRedisTemplate.execute(
                connection -> connection.bitCount(key.getBytes()),
                true
        );
        data.put("totalSignDays", totalDays == null ? 0 : totalDays.intValue());

        return Result.ok(data);
    }
}
