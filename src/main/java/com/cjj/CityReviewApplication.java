package com.cjj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cjj.mapper")
@SpringBootApplication
public class CityReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityReviewApplication.class, args);
    }

}
