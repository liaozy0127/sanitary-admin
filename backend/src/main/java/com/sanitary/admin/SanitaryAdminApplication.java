package com.sanitary.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sanitary.admin.mapper")
public class SanitaryAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(SanitaryAdminApplication.class, args);
    }
}
