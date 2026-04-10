package com.oyproj.config;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        URI uri = URI.create(elasticsearchUris);
        String host = uri.getHost();
        int port = uri.getPort();
        String scheme = uri.getScheme();

        // 1. 创建支持 Java 时间类型的 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // 可选：将日期时间序列化为标准字符串（如 "2026-04-07T22:08:23"），而不是时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 忽略未知属性，避免反序列化失败
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 允许空字符串转换为null
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // 2. 用该 ObjectMapper 构造 JacksonJsonpMapper
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
        // 3. 创建 RestClient 和 Transport
        RestClient restClient = RestClient.builder(
                new HttpHost(host, port, scheme)
        ).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
        // 4. 返回 ElasticsearchClient
        return new ElasticsearchClient(transport);
    }
}