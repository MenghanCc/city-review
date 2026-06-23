package com.cjj.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * city-review 统一响应结果
 * 格式：{ "code": 200, "msg": "ok", "data": {} }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    /** 状态码：200=成功，401=未登录，500=服务器异常 */
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 响应数据 */
    private Object data;

    public static Result ok() {
        return new Result(200, "ok", null);
    }

    public static Result ok(Object data) {
        return new Result(200, "ok", data);
    }

    public static Result ok(Object data, String msg) {
        return new Result(200, msg, data);
    }

    public static Result fail(String msg) {
        return new Result(500, msg, null);
    }

    public static Result fail(Integer code, String msg) {
        return new Result(code, msg, null);
    }
}
