package com.contentflow.content.mapper;

import com.contentflow.content.entity.ContentEntity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ContentMapper extends BaseMapper<ContentEntity> {
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{requestId}, 0))) AS request_lock")
    int lockAgentRequestId(@Param("requestId") String requestId);
}
