package com.homegpt.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HomeGptApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeGptApplication.class, args);
    }
}
