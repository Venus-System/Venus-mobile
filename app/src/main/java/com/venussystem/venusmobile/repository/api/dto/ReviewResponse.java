package com.venussystem.venusmobile.repository.api.dto;

public class ReviewResponse {
    public Long id;
    public Long userId;
    public String authorName;
    public Long productVersionId;
    public Double rating;
    public String title;
    public String comment;
    public Boolean verifiedUse;
    public Long usefulVotes;
    public String createdAt;
}
