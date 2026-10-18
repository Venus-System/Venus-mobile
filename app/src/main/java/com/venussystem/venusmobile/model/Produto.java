package com.venussystem.venusmobile.model;

public class Produto {
    private final Long id;
    private final String name;
    private final String brandName;
    private final Integer overallScore;
    private final String imageUrl;
    private final Long categoryId;
    private final String categoryName;
    private final Long brandId;
    private final Boolean brazilianBrand;

    public Produto(Long id, String name, String brandName, Integer overallScore, String imageUrl,
                   Long categoryId, String categoryName) {
        this(id, name, brandName, overallScore, imageUrl, categoryId, categoryName, null, null);
    }

    public Produto(Long id, String name, String brandName, Integer overallScore, String imageUrl,
                   Long categoryId, String categoryName, Long brandId, Boolean brazilianBrand) {
        this.id = id;
        this.name = name;
        this.brandName = brandName;
        this.overallScore = overallScore;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.brandId = brandId;
        this.brazilianBrand = brazilianBrand;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrandName() {
        return brandName;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Long getBrandId() {
        return brandId;
    }

    /** Null quando a API nao informou (ou o produto nao veio do catalogo). */
    public Boolean getBrazilianBrand() {
        return brazilianBrand;
    }
}
