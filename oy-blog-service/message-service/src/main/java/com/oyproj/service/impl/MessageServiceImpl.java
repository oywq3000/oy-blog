package com.oyproj.service.impl;

import com.oyproj.domain.dto.MessageSendDto;
import com.oyproj.service.MessageService;
import com.oyproj.strategy.MessageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一消息服务实现：按消息类型分发给对应策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final List<MessageStrategy> messageStrategies;

    @Override
    public void sendMessage(MessageSendDto sendDto) {
        MessageStrategy strategy = messageStrategies.stream()
                .filter(s -> s.support(sendDto.getMessageType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的消息类型: " + sendDto.getMessageType()));
        log.info("消息分发: type={}, strategy={}", sendDto.getMessageType(), strategy.getClass().getSimpleName());
        strategy.send(sendDto);
    }
}
