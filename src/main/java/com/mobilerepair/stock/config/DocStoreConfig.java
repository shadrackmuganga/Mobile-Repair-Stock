package com.mobilerepair.stock.config;

import com.google.cloud.firestore.Firestore;
import com.mobilerepair.stock.store.DocStore;
import com.mobilerepair.stock.store.FirestoreDocStore;
import com.mobilerepair.stock.store.InMemoryDocStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocStoreConfig {

    @Bean
    @ConditionalOnBean(Firestore.class)
    public DocStore firestoreDocStore(Firestore firestore) {
        return new FirestoreDocStore(firestore);
    }

    @Bean
    @ConditionalOnMissingBean(DocStore.class)
    public DocStore inMemoryDocStore() {
        return new InMemoryDocStore();
    }
}
