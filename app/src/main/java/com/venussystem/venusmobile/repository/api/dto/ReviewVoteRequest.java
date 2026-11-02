package com.venussystem.venusmobile.repository.api.dto;

public class ReviewVoteRequest {
    public Long reviewId;
    public Long userId;
    public String voteType;

    public ReviewVoteRequest(Long reviewId, Long userId, String voteType) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.voteType = voteType;
    }
}
