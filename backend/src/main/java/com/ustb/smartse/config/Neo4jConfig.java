package com.ustb.smartse.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Neo4j配置类
 */
@Configuration
@EnableTransactionManagement
@EnableNeo4jRepositories(basePackages = "com.ustb.smartse.modules.knowledgebase.repository")
public class Neo4jConfig {

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${spring.neo4j.authentication.username}")
    private String username;

    @Value("${spring.neo4j.authentication.password}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    @Bean
    public Neo4jClient neo4jClient(Driver driver) {
        return Neo4jClient.create(driver);
    }
    
    @Bean
    public Neo4jMappingContext neo4jMappingContext() {
        return new Neo4jMappingContext();
    }
    
    @Bean
    public Neo4jTemplate neo4jTemplate(Neo4jClient neo4jClient, Neo4jMappingContext neo4jMappingContext) {
        return new Neo4jTemplate(neo4jClient, neo4jMappingContext);
    }

    @Bean
    public Neo4jTransactionManager transactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
} 