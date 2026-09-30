package com.IMCDOperationsTool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.IMCDOperationsTool", "com.imcd.platformlib" })
public class ImcdOperationsToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImcdOperationsToolApplication.class, args);
    }
}
