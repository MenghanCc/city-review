package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.ISignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 签到控制器
 */
@RestController
@RequestMapping("/api/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /**
     * 签到
     */
    @PostMapping
    public Result sign() {
        return signService.sign();
    }

    /**
     * 查询本月连续签到天数
     */
    @GetMapping("/count")
    public Result signCount() {
        return signService.signCount();
    }

    /**
     * 查询本月签到日历
     */
    @GetMapping("/calendar")
    public Result signCalendar() {
        return signService.signCalendar();
    }
}
