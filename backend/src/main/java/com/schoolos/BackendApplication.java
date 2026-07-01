package com.schoolos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.context.ApplicationListener<org.springframework.context.event.ContextClosedEvent> closeH2Database(javax.sql.DataSource dataSource) {
        return event -> {
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stat = conn.createStatement()) {
                stat.execute("SHUTDOWN");
                System.out.println("Gracefully shut down H2 database.");
            } catch (Exception e) {
                System.err.println("Failed to cleanly shutdown H2: " + e.getMessage());
            }
        };
    }
}
