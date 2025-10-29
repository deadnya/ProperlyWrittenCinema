package com.absolute.cinema.service.impl;

import lombok.Data;
import java.util.UUID;

@Data
class SessionMetadata {
    private UUID sessionId;
    private String metadata;
    private String description;
    private long createdTimestamp;
    
    public SessionMetadata(UUID sessionId, String metadata) {
        this.sessionId = sessionId;
        this.metadata = metadata;
        this.createdTimestamp = System.currentTimeMillis();
    }
    
    public UUID getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }
    
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
