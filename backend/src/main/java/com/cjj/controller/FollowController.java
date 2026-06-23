package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.impl.FollowServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 好友关注控制器
 */
@RestController
@RequestMapping("/api/follow")
public class FollowController {

    @Resource
    private FollowServiceImpl followService;

    /**
     * 关注 / 取关
     * @param id        目标用户ID
     * @param isFollow  true=关注, false=取关
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    /**
     * 是否已关注
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }

    /**
     * 查询共同关注
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id) {
        return followService.commonFollow(id);
    }

    /**
     * 关注列表
     */
    @GetMapping("/list/{id}")
    public Result followList(@PathVariable("id") Long id) {
        return followService.followList(id);
    }

    /**
     * 粉丝列表
     */
    @GetMapping("/fans/{id}")
    public Result fansList(@PathVariable("id") Long id) {
        return followService.fansList(id);
    }
}
