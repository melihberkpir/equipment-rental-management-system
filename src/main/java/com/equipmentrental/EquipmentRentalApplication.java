package com.equipmentrental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EquipmentRentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquipmentRentalApplication.class, args);
    }
}
