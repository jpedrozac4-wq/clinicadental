package com.clinica.dental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClinicaDentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicaDentalApplication.class, args);
    }
}
