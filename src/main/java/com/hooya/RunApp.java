package com.hooya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunApp {
    public static void main(String[] args) {
        SpringApplication.run(RunApp.class);
    }
}
