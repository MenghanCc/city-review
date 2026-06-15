package com.cjj.service.impl;

import com.cjj.entity.Blog;
import com.cjj.mapper.BlogMapper;
import com.cjj.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 陈俊杰
 * @since 2026-6-3
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
