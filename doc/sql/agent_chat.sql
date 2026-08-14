-- ============================================================
-- AI 对话（agent-service）建表脚本
-- 数据库: oyblog
-- 架构: Vue -> 网关 -> agent-service (BFF) -> Python Agent
-- 说明:
--   * 会话 id 由前端客户端生成（conv_ 前缀），后端首条消息时 upsert
--   * 消息 id 由服务端生成（MyBatis-Plus ASSIGN_UUID）
--   * 会话为物理删除，消息靠外键级联删除
-- 执行: mysql -h<host> -uroot -proot --default-character-set=utf8mb4 oyblog < agent_chat.sql
-- ============================================================
SET NAMES utf8mb4;

-- AI 对话会话表
CREATE TABLE IF NOT EXISTS agent_conversation (
    id          VARCHAR(64)  NOT NULL COMMENT '会话ID（前端生成 conv_*）',
    user_id     VARCHAR(64)  NOT NULL COMMENT '归属用户ID或游客ID（来自网关 x-user-id）',
    title       VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- AI 对话消息表
CREATE TABLE IF NOT EXISTS agent_message (
    id              VARCHAR(64)  NOT NULL COMMENT '消息ID（服务端生成）',
    conversation_id VARCHAR(64)  NOT NULL COMMENT '所属会话ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '归属用户ID（冗余，便于消息级 owner 校验）',
    role            VARCHAR(16)  NOT NULL COMMENT '角色：user / assistant',
    content         MEDIUMTEXT   NULL COMMENT '消息内容',
    thinking        TEXT         NULL COMMENT '思考过程（深度思考时）',
    thinking_time   INT          NULL COMMENT '思考耗时（秒）',
    feedback        VARCHAR(16)  NULL COMMENT '用户反馈：like / dislike',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_conv_created (conversation_id, created_at),
    CONSTRAINT fk_agent_msg_conv FOREIGN KEY (conversation_id)
        REFERENCES agent_conversation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';
