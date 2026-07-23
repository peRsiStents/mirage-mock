package com.miragemock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 蜃楼 Mock 启动类。
 *
 * <p>管理端 REST 与 Mock HTTP 运行时内核同进程部署：
 * 管理端在 server.port（默认 9080），Mock 流量在 mirage.http.port（默认 19080）。
 */
@SpringBootApplication
@MapperScan("com.miragemock.admin.mapper")
@EnableScheduling
public class MirageMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(MirageMockApplication.class, args);
    }
}
