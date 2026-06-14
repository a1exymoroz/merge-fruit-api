package com.mergefruit.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 Learning Notes

 What: Entry point — starts the embedded Tomcat server and loads the Spring context.
 Why: Spring Boot scans this package (and sub-packages) for @Component, @Service, etc.

 Try yourself:
 - Add a @PostConstruct bean that logs active profiles on startup.
 - Create a custom health indicator (Actuator) later.

 Common mistake:
 - Putting the main class outside the root package so other packages aren't scanned.
*/
@SpringBootApplication
public class MergeFruitBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MergeFruitBackendApplication.class, args);
    }
}
