package com.revpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.revpay")
public class RevPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RevPayApplication.class, args);
    }
}
