package com.cjj.service.impl;

import com.cjj.entity.BlogComments;
import com.cjj.mapper.BlogCommentsMapper;
import com.cjj.service.IBlogCommentsService;
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
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
