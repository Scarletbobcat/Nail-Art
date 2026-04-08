package com.nail_art.appointment_book.configs;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;

@Configuration
public class MongoConfig {
    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        mongoTemplate.indexOps("Clients").ensureIndex(
                new Index()
                        .on("phoneNumber", Sort.Direction.ASC)
                        .unique()
                        .named("phoneNumber_unique_partial")
                        .partial(PartialIndexFilter.of(
                                Document.parse("{ phoneNumber: { $type: 'string', $gt: '' } }")
                        ))
        );
    }

    @PreDestroy
    public void closeMongoClient() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
