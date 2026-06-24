package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_product")
public class Product implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private BigDecimal price;
    private String coverImg;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
