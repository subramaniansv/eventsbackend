package com.social.app.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages the HikariCP connection-pool lifecycle alongside the Spring context.
 * @PostConstruct warms up the pool at startup; @PreDestroy closes it gracefully.
 */
@Component
public class DBLifecycleListener {
    private static final Logger LOG = LoggerFactory.getLogger(DBLifecycleListener.class);

    @PostConstruct
    public void init() {
        DBConfig.getDataSource();
        LOG.info("HikariCP pool initialized");
    }

    @PreDestroy
    public void destroy() {
        try {
            DBConfig.shutdown();
            LOG.info("HikariCP pool shut down");
        } catch (Exception e) {
            LOG.error("Failed to shut down HikariCP pool", e);
        }
    }
}
