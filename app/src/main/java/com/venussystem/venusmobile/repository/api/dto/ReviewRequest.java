package com.venussystem.venusmobile.repository.api.dto;

public class ReviewRequest {
    public Long userId;
    public Long productVersionId;
    public Integer rating;
    public String title;
    public String comment;
    public Boolean verifiedUse;

    public ReviewRequest(Long userId, Long productVersionId, Integer rating, String title,
                         String comment, Boolean verifiedUse) {
        this.userId = userId;
        this.productVersionId = productVersionId;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.verifiedUse = verifiedUse;
    }
}
