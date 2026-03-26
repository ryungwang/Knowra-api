package com.knowra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowraApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowraApiApplication.class, args);
    }

}
