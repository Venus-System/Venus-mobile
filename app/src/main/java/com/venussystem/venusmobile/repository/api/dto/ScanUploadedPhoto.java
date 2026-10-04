package com.venussystem.venusmobile.repository.api.dto;

import java.io.IOException;

/** Durable upload receipt. No token, upload signature or API key is persisted. */
public final class ScanUploadedPhoto {
    public ScanSessionRequest.Image image;
    public String cloudName, deliveryType;
    public long version;

    public void validate(String scanId, String side) throws IOException {
        if (!("front".equals(side) || "back".equals(side)) || image == null
                || !("scans/" + scanId + "/" + side).equals(image.publicId)
                || image.width == null || image.width <= 0 || image.height == null || image.height <= 0
                || image.bytes == null || image.bytes <= 0 || image.format == null
                || !image.format.matches("[a-zA-Z0-9]{1,10}") || version <= 0
                || cloudName == null || !cloudName.matches("[a-zA-Z0-9_-]+")
                || !("upload".equals(deliveryType) || "private".equals(deliveryType) || "authenticated".equals(deliveryType))) {
            throw new IOException("Confirmação da foto inválida ou de outro scan.");
        }
    }
}
