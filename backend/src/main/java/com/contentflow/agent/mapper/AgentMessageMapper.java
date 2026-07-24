package com.contentflow.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentflow.agent.entity.AgentMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AgentMessageMapper extends BaseMapper<AgentMessageEntity> {
    @Update("""
            UPDATE cf_agent_message
            SET processing_status = 'PROCESSING'
            WHERE id = #{id} AND processing_status = 'FAILED'
            """)
    int claimFailedRequest(@Param("id") Long id);
}
