package com.nail_art.appointment_book.configs;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {
    @Autowired
    private MongoClient mongoClient;

    @PreDestroy
    public void closeMongoClient() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
