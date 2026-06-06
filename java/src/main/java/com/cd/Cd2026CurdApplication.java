package com.cd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@EnableAsync
@MapperScan("com.cd.mapper")
@SpringBootApplication
public class Cd2026CurdApplication {

    public static void main(String[] args) {
        SpringApplication.run(Cd2026CurdApplication.class, args);
    }
}
