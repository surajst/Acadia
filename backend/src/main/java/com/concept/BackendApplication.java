package com.concept;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * Closes the local H2 file cleanly on shutdown.
     *
     * <p>Guarded on the database actually being H2. It used to issue SHUTDOWN
     * against whatever DataSource was configured, so every production deploy
     * logged "Failed to cleanly shutdown H2: syntax error at or near SHUTDOWN"
     * as an ERROR -- Postgres has no such statement. Harmless in itself, but a
     * recurring error nobody needs to read is how real errors get skimmed past.
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.context.ApplicationListener<org.springframework.context.event.ContextClosedEvent> closeH2Database(javax.sql.DataSource dataSource) {
        return event -> {
            try (java.sql.Connection conn = dataSource.getConnection()) {
                String product = conn.getMetaData().getDatabaseProductName();
                if (!"H2".equalsIgnoreCase(product)) {
                    return;
                }
                try (java.sql.Statement stat = conn.createStatement()) {
                    stat.execute("SHUTDOWN");
                }
                System.out.println("Gracefully shut down H2 database.");
            } catch (Exception e) {
                System.err.println("Failed to cleanly shutdown H2: " + e.getMessage());
            }
        };
    }
}
