package com.mobilerepair.stock.config;

import com.google.cloud.firestore.Firestore;
import com.mobilerepair.stock.store.DocStore;
import com.mobilerepair.stock.store.FirestoreDocStore;
import com.mobilerepair.stock.store.InMemoryDocStore;
import com.mobilerepair.stock.store.PostgresDocStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(DocStoreConfig.class);

    // Storage precedence: a configured PostgreSQL URL (durable) wins, then
    // Firestore when Firebase is enabled, and finally an in-memory store
    // (non-durable, used for local development and tests).
    @Bean
    public DocStore docStore(
            ObjectProvider<Firestore> firestoreProvider,
            @Value("${app.database.url:}") String databaseUrl,
            @Value("${app.database.user:}") String databaseUser,
            @Value("${app.database.password:}") String databasePassword) {
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            log.info("Using PostgreSQL-backed document store for durable storage");
            return new PostgresDocStore(databaseUrl, databaseUser, databasePassword);
        }
        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore != null) {
            log.info("Using Firestore-backed document store");
            return new FirestoreDocStore(firestore);
        }
        log.info("Using in-memory document store (data will not survive restarts)");
        return new InMemoryDocStore();
    }
}
