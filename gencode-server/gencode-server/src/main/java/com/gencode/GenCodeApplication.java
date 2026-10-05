package com.gencode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GenCode 低代码平台启动入口
 */
@SpringBootApplication(scanBasePackages = "com.gencode")
@MapperScan("com.gencode.**.mapper")
public class GenCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenCodeApplication.class, args);
    }
}
