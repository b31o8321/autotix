package dev.autotix;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TODO: Spring Boot main entry.
 *  - Component scan covers dev.autotix.* (DDD four layers)
 *  - MyBatis Plus mapper scan over dev.autotix.infrastructure.persistence.**.mapper
 *  - Register platform plugin SPI on startup
 *  - Wire infra provider beans based on application.yml (lock/queue/cache/storage)
 */
@SpringBootApplication
@MapperScan("dev.autotix.infrastructure.persistence.**.mapper")
public class AutotixServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutotixServerApplication.class, args);
    }
}
