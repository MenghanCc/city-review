package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.entity.Blog;
import com.cjj.service.impl.BlogServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 探店笔记控制器
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    @Resource
    private BlogServiceImpl blogService;

    /**
     * 发布探店笔记
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞 / 取消点赞（Toggle 模式）
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 点赞排行榜（Top N）
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id,
                                  @RequestParam(value = "top", defaultValue = "5") Integer top) {
        return blogService.queryBlogLikes(id, top);
    }

    /**
     * 关注流（Feed 收件箱）滚动分页
     * @param max    上次查询的最小时间戳（首次不传）
     * @param offset 偏移量（同时间戳的跳过条数）
     */
    @GetMapping("/of/follow")
    public Result queryFeed(@RequestParam(value = "max", required = false) Long max,
                            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryFeed(max, offset);
    }

    /**
     * 热门笔记
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 我的笔记
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 笔记详情
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }
}
