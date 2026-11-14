package com.unilodge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * UniLodge entry point.
 *
 * <p>The application class lives in the root {@code com.unilodge} package so
 * component scanning reaches the service and web modules.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("com.unilodge.service.config")
@EntityScan("com.unilodge.persistence.entity")
@EnableJpaRepositories("com.unilodge.persistence.repository")
public class UniLodgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniLodgeApplication.class, args);
    }
}
