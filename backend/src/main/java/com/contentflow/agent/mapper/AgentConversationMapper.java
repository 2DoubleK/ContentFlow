package com.contentflow.agent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentConversationMapper {
    @Select("""
            SELECT COUNT(*) > 0
            FROM cf_agent_conversation
            WHERE id = #{id} AND user_id = #{userId} AND project_id = #{projectId}
            """)
    boolean existsOwned(@Param("id") Long id, @Param("userId") Long userId, @Param("projectId") Long projectId);
}
