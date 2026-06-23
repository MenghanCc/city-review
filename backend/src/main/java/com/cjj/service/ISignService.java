package com.cjj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cjj.dto.Result;
import com.cjj.entity.Sign;

/**
 * city-review 签到服务接口
 */
public interface ISignService extends IService<Sign> {

    /**
     * 用户签到
     */
    Result sign();

    /**
     * 查询本月连续签到天数
     */
    Result signCount();

    /**
     * 查询本月签到日历
     */
    Result signCalendar();
}
