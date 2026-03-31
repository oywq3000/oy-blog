package com.oyproj.service;


import com.oyproj.domain.dto.MessageSendDto;

/**
 *  统一消息服务接口
 */
public interface MessageService {

    /**
     * 发送消息（异步）
     * @param sendDto 消息数据
     */
    void sendMessage(MessageSendDto sendDto);
}
