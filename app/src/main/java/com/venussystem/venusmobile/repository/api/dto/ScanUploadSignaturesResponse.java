package com.venussystem.venusmobile.repository.api.dto;

public final class ScanUploadSignaturesResponse {
    public String cloudName, apiKey, uploadPreset, type;
    public long timestamp;
    public Boolean overwrite;
    public Signature front, back;
    public static final class Signature { public String publicId, signature; }
}
