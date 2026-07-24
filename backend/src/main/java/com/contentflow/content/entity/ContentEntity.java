package com.contentflow.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import org.apache.ibatis.type.JdbcType;
import com.contentflow.common.persistence.JsonbTypeHandler;

@TableName(value = "cf_content", autoResultMap = true)
public class ContentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long ownerId;
    private String title;
    private String summary;
    private String content;
    private String contentType;
    private String status;
    private String markdown;
    private Long conversationId;
    private String agentRequestId;
    @TableField(value = "references_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbTypeHandler.class)
    private String referencesJson;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMarkdown() { return markdown; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getAgentRequestId() { return agentRequestId; }
    public void setAgentRequestId(String agentRequestId) { this.agentRequestId = agentRequestId; }
    public String getReferencesJson() { return referencesJson; }
    public void setReferencesJson(String referencesJson) { this.referencesJson = referencesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
