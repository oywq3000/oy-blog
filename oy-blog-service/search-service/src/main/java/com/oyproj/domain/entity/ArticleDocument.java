package com.oyproj.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章搜索文档
 */
@Data
@Document(indexName = "articles")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleDocument implements Serializable {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;
    
    @Field(type = FieldType.Text)
    private String summary;
    
    @Field(type = FieldType.Keyword)
    private String author;
    
    @Field(type = FieldType.Keyword)
    private String authorId;
    
    @Field(type = FieldType.Date,format = DateFormat.date_hour_minute_second_millis)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "GMT+8")
    private LocalDateTime createdAt;
    
    @Field(type = FieldType.Date,format = DateFormat.date_hour_minute_second_millis)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "GMT+8")
    private LocalDateTime updatedAt;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
    @Field(type = FieldType.Integer)
    private Integer viewCount;
    
    @Field(type = FieldType.Integer)
    private Integer likeCount;
    
    @Field(type = FieldType.Integer)
    private Integer commentCount;
    
    @Field(type = FieldType.Keyword)
    private List<String> tags;
    
    @Field(type = FieldType.Keyword)
    private String category;
}