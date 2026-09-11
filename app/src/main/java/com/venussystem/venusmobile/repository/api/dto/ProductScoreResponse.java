package com.venussystem.venusmobile.repository.api.dto;

public class ProductScoreResponse {
    public Long id;
    public Long productVersionId;
    public Long scoringModelId;
    public Integer overallScore;
    public Integer healthScore;
    public Integer environmentalScore;
    public Integer ethicalScore;
    public Integer performanceScore;
    public Integer transparencyScore;
    public Integer confidenceScore;
}
