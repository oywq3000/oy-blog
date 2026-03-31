package com.oyproj.config;

import com.oyproj.domain.model.StorageDomain;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    @Bean
    @ConfigurationProperties(prefix = "storage")
    public StorageDomain storageDomain() {
        return new StorageDomain();
    }
}
