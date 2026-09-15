package com.misa.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class MisaBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MisaBackendApplication.class, args);
    }
}
