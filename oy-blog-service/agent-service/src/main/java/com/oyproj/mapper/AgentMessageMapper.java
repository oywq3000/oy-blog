package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    /**
     * 批量统计会话消息数（避免 N+1）
     */
    @Select("<script>" +
            "SELECT conversation_id, COUNT(*) AS cnt FROM agent_message " +
            "WHERE conversation_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY conversation_id" +
            "</script>")
    List<Map<String, Object>> countByConversationIds(@Param("ids") List<String> ids);
}
