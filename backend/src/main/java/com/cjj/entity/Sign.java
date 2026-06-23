package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * city-review 用户签到实体
 * 对应 tb_sign 表，作为 Redis BitMap 签到的 MySQL 备份
 *
 * 为什么 MySQL 只是备份？
 *   签到是高频写操作（每日百万级），BitMap 内存仅 46 字节/用户/月，
 *   远优于 MySQL 行存储。MySQL 仅在月末同步或用户查询历史时回查。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_sign")
public class Sign implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 签到的年
     */
    private Integer year;

    /**
     * 签到的月
     */
    private Integer month;

    /**
     * 签到的日期
     */
    private LocalDate date;

    /**
     * 是否补签
     */
    private Boolean isBackup;
}
