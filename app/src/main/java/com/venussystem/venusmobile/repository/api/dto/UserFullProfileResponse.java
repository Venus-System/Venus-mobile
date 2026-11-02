package com.venussystem.venusmobile.repository.api.dto;

import java.util.List;

public class UserFullProfileResponse {
    public UserProfileResponse profile;
    public UserPreferenceResponse preferences;
    public List<PreferenceCatalogResponse> tags;
    public List<UserAllergyDetailResponse> allergies;
}
