package com.cjj.utils;

/**
 * city-review 系统常量
 */
public class SystemConstants {
    /** 图片上传目录（相对项目的 static/imgs 路径，Spring Boot 自动托管） */
    public static final String IMAGE_UPLOAD_DIR = System.getProperty("user.dir") + "/src/main/resources/static/imgs";
    /** 用户昵称前缀 */
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 5;
    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 10;
    /** 附近商户默认搜索半径（米） */
    public static final int DEFAULT_GEO_RADIUS = 5000;
}
