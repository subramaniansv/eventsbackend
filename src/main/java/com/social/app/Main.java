package com.social.app;

import com.social.app.common.ENVConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Properties;

/**
 * Application entry point.
 *
 * Boots Spring Boot with an embedded Tomcat.
 * All environment variables (DB, JWT secrets, Meta keys, etc.) are loaded
 * via ENVConfig which reads from the process environment first, then falls
 * back to the .env file via dotenv-java.
 *
 * Spring Boot DataSource auto-configuration is excluded because HikariCP
 * is managed manually in DBConfig.
 */
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
public class Main {

    public static void main(String[] args) {
        // Trigger ENVConfig static initializer so dotenv loads .env
        // BEFORE Spring Boot reads any property placeholders.
        ENVConfig.get("APP_NAME");

        SpringApplication app = new SpringApplication(Main.class);

        // Spring Boot's ${SERVER_PORT:8080} placeholder only reads real OS
        // env vars, not .env files; pass the value here so dotenv-java takes effect.
        Properties defaults = new Properties();
        defaults.setProperty("server.port", ENVConfig.get("SERVER_PORT", "8080"));
        app.setDefaultProperties(defaults);

        app.run(args);
    }
}
