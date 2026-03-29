package com.oyproj.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.domain.dto.UserDTO;

public class JsonUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 将对象转换为JSON字符串
     * @param object 要转换的对象
     * @return JSON字符串
     * @throws JsonProcessingException JSON处理异常
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }
    
    /**
     * 将JSON字符串转换为指定类型的对象
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象
     * @throws JsonProcessingException JSON处理异常
     */
    public static <T> T parse(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }
    
    /**
     * 将UserDTO转换为JSON字符串
     * @param userDTO UserDTO对象
     * @return JSON字符串
     */
    public static String userDTOToJson(UserDTO userDTO) {
        try {
            return objectMapper.writeValueAsString(userDTO);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将JSON字符串转换为UserDTO对象
     * @param json JSON字符串
     * @return UserDTO对象
     */
    public static UserDTO parseToUserDTO(String json) {
        try {
            return objectMapper.readValue(json, UserDTO.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}