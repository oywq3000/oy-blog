package com.oyproj.config;

import com.oyproj.domain.vo.SuggestedQuestionVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * agent 相关配置（application.yml 的 agent.* 节点）
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * Python Agent 服务配置
     */
    private Python python = new Python();

    /**
     * 推荐问题列表（静态配置，后续可扩展为优先询问 Python）
     */
    private List<SuggestedQuestionVo> suggestions = new ArrayList<>();

    @Data
    public static class Python {
        /**
         * Python 服务地址，如 http://localhost:8001
         */
        private String baseUrl = "http://localhost:8001";
    }
}
