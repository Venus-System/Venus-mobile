package com.venussystem.venusmobile.repository.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadedPhoto;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Signed multipart upload to a fixed HTTPS host. Never uses the Venus/Firebase HTTP client. */
public final class CloudinaryUploadClient {
    private final Call.Factory http;
    private final Clock clock;

    public CloudinaryUploadClient() {
        this(new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build(), Clock.systemUTC());
    }
    public CloudinaryUploadClient(Call.Factory http, Clock clock) { this.http = http; this.clock = clock; }

    public ScanUploadedPhoto upload(ScanUploadSignaturesResponse s, String scanId, String side, File file)
            throws IOException {
        if (s == null || s.cloudName == null || !s.cloudName.matches("[a-zA-Z0-9_-]+")
                || blank(s.apiKey) || s.overwrite == null
                || !("upload".equals(s.type) || "private".equals(s.type) || "authenticated".equals(s.type)))
            throw failure(ScanUploadException.Kind.CONFIGURATION, 0);
        ScanUploadSignaturesResponse.Signature signed = "front".equals(side) ? s.front : "back".equals(side) ? s.back : null;
        if (signed == null || !("scans/" + scanId + "/" + side).equals(signed.publicId) || blank(signed.signature))
            throw failure(ScanUploadException.Kind.CONFIGURATION, 0);
        long now = clock.instant().getEpochSecond();
        if (s.timestamp <= 0 || s.timestamp < now - 3300 || s.timestamp > now + 300)
            throw failure(ScanUploadException.Kind.EXPIRED_SIGNATURE, 0);
        if (file == null || !file.isFile() || file.length() == 0)
            throw failure(ScanUploadException.Kind.LOCAL, 0);

        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("api_key", s.apiKey)
                .addFormDataPart("timestamp", Long.toString(s.timestamp))
                .addFormDataPart("signature", signed.signature)
                .addFormDataPart("public_id", signed.publicId)
                .addFormDataPart("type", s.type)
                .addFormDataPart("overwrite", Boolean.toString(s.overwrite));
        if (!blank(s.uploadPreset)) form.addFormDataPart("upload_preset", s.uploadPreset);
        // Cloudinary detects image content; private .photo snapshots do not retain an extension.
        form.addFormDataPart("file", side + ".photo", RequestBody.create(MediaType.get("application/octet-stream"), file));
        HttpUrl url = new HttpUrl.Builder().scheme("https").host("api.cloudinary.com")
                .addPathSegment("v1_1").addPathSegment(s.cloudName).addPathSegment("image").addPathSegment("upload").build();
        Request request = new Request.Builder().url(url).post(form.build()).build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw failure(ScanUploadException.Kind.HTTP, response.code());
            if (response.body() == null) throw failure(ScanUploadException.Kind.INVALID_RESPONSE, 0);
            UploadResponse body;
            try { body = new Gson().fromJson(response.body().charStream(), UploadResponse.class); }
            catch (RuntimeException e) { throw failure(ScanUploadException.Kind.INVALID_RESPONSE, 0); }
            if (body == null || !"image".equals(body.resourceType) || !s.type.equals(body.type))
                throw failure(ScanUploadException.Kind.INVALID_RESPONSE, 0);
            ScanUploadedPhoto receipt = new ScanUploadedPhoto();
            receipt.cloudName = s.cloudName; receipt.deliveryType = body.type; receipt.version = body.version;
            receipt.image = new ScanSessionRequest.Image();
            receipt.image.publicId = body.publicId; receipt.image.width = body.width; receipt.image.height = body.height;
            receipt.image.format = body.format; receipt.image.bytes = body.bytes;
            try { receipt.validate(scanId, side); }
            catch (IOException e) { throw failure(ScanUploadException.Kind.INVALID_RESPONSE, 0); }
            return receipt;
        } catch (ScanUploadException e) { throw e; }
        catch (InterruptedIOException e) { throw failure(ScanUploadException.Kind.TIMEOUT, 0); }
        catch (IOException e) { throw failure(ScanUploadException.Kind.NETWORK, 0); }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static ScanUploadException failure(ScanUploadException.Kind kind, int code) {
        return new ScanUploadException(kind, code, "Não foi possível confirmar o envio da foto.");
    }
    private static final class UploadResponse {
        @SerializedName("public_id") String publicId;
        @SerializedName("resource_type") String resourceType;
        String type, format;
        Integer width, height;
        Long bytes;
        long version;
    }
}
