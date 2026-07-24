package com.example.demo.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    private static final String DEFAULT_URI = "mongodb://localhost:27017/lms_app";
    private static final String DATABASE_NAME = "lms_app";

    private String resolveUri() {
        String uri = System.getenv("SPRING_DATA_MONGODB_URI");
        if (uri != null && !uri.isBlank()) {
            log.info("MongoDB: using SPRING_DATA_MONGODB_URI from environment (Atlas)");
            return uri;
        }
        log.warn("MongoDB: SPRING_DATA_MONGODB_URI not found, falling back to localhost");
        return DEFAULT_URI;
    }

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(resolveUri());
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, DATABASE_NAME);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        return new MongoTemplate(factory);
    }
}
