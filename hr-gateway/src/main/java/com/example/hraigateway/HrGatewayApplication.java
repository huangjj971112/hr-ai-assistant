package com.example.hraigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HrGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrGatewayApplication.class, args);
    }
}
