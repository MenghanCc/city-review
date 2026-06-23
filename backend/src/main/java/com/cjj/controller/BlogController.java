package com.cjj.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjj.dto.Result;
import com.cjj.entity.Blog;
import com.cjj.entity.Shop;
import com.cjj.entity.User;
import com.cjj.service.IShopService;
import com.cjj.service.IUserService;
import com.cjj.service.impl.BlogServiceImpl;
import com.cjj.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * city-review 探店笔记控制器
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    @Resource
    private BlogServiceImpl blogService;

    @Resource
    private IUserService userService;

    @Resource
    private IShopService shopService;

    /**
     * 首页帖子流（分页 + 关联商户和用户信息）
     * GET /api/blog/list?page=1&size=10
     */
    @GetMapping("/list")
    public Result listBlogs(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Page<Blog> blogPage = blogService.getBaseMapper().selectPage(
                new Page<>(page, size),
                Wrappers.<Blog>lambdaQuery().orderByDesc(Blog::getCreateTime));

        List<Map<String, Object>> enriched = blogPage.getRecords().stream().map(blog -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", blog.getId());
            m.put("title", blog.getTitle());
            m.put("content", blog.getContent());
            m.put("images", blog.getImages());
            m.put("liked", blog.getLiked());
            m.put("comments", blog.getComments());
            m.put("createTime", blog.getCreateTime());

            // 关联用户
            User user = userService.getById(blog.getUserId());
            m.put("userId", blog.getUserId());
            m.put("nickname", user != null ? user.getNickName() : "匿名");
            m.put("avatar", user != null ? user.getIcon() : "");

            // 关联商户
            Shop shop = shopService.getById(blog.getShopId());
            m.put("shopId", blog.getShopId());
            m.put("shopName", shop != null ? shop.getName() : "未知商户");

            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", enriched);
        result.put("total", blogPage.getTotal());
        result.put("pages", blogPage.getPages());
        return Result.ok(result);
    }

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
