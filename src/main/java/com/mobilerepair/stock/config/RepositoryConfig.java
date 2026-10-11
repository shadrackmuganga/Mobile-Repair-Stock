package com.mobilerepair.stock.config;

import com.google.cloud.firestore.Firestore;
import com.mobilerepair.stock.inventory.FirestoreItemRepository;
import com.mobilerepair.stock.inventory.InMemoryItemRepository;
import com.mobilerepair.stock.inventory.ItemRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryConfig {

    @Bean
    @ConditionalOnBean(Firestore.class)
    public ItemRepository firestoreItemRepository(Firestore firestore) {
        return new FirestoreItemRepository(firestore);
    }

    @Bean
    @ConditionalOnMissingBean(ItemRepository.class)
    public ItemRepository inMemoryItemRepository() {
        return new InMemoryItemRepository();
    }
}
