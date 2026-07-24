package com.contentflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.contentflow.auth.mapper",
        "com.contentflow.project.mapper",
        "com.contentflow.document.mapper",
        "com.contentflow.content.mapper"
})
public class ContentFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContentFlowApplication.class, args);
    }
}
