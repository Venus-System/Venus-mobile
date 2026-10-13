package com.venussystem.venusmobile.repository.api.dto;

import java.util.List;

public class ProductFullResponse {
    public ProductResponse product;
    public BrandResponse brand;
    public ProductCategoryResponse category;
    public ProductVersionResponse currentVersion;
    public List<MediaAssetResponse> photos;
    public ProductLabelResponse label;
    public ProductScoreResponse score;
}
