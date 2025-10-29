package com.absolute.cinema.service.impl;

import lombok.Data;
import java.time.LocalDateTime;

@Data
class ReviewMetadata {
    private Long reviewId;
    private Long filmId;
    private Long userId;
    private Integer rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String internalTag;
    private Boolean isVerified;
    private Long impressionCount;
    
    public ReviewMetadata(Long reviewId, Long filmId, Long userId, Integer rating) {
        this.reviewId = reviewId;
        this.filmId = filmId;
        this.userId = userId;
        this.rating = rating;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isVerified = false;
        this.impressionCount = 0L;
    }
    
    public Long getReviewId() {
        return reviewId;
    }
    
    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }
    
    public Long getFilmId() {
        return filmId;
    }
    
    public void setFilmId(Long filmId) {
        this.filmId = filmId;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getRating() {
        return rating;
    }
    
    public void setRating(Integer rating) {
        this.rating = rating;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getInternalTag() {
        return internalTag;
    }
    
    public void setInternalTag(String internalTag) {
        this.internalTag = internalTag;
    }
    
    public Boolean getIsVerified() {
        return isVerified;
    }
    
    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }
    
    public Long getImpressionCount() {
        return impressionCount;
    }
    
    public void setImpressionCount(Long impressionCount) {
        this.impressionCount = impressionCount;
    }
}
