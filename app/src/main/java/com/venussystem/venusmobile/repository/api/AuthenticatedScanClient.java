package com.venussystem.venusmobile.repository.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.UserRequest;
import com.venussystem.venusmobile.repository.api.dto.UserResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.Response;
import okhttp3.ResponseBody;

public final class AuthenticatedScanClient {
    private final ScanSubmissionApi api;
    private final ScanAuthSession session;
    public AuthenticatedScanClient(ScanSubmissionApi api, ScanAuthSession session) {
        this.api = api; this.session = session;
    }
    public ScanUploadSignaturesResponse signatures(String uid, String scanId) throws IOException {
        validateScanId(scanId);
        ScanUploadSignaturesResponse result = execute(uid, token -> api.signatures(token, scanId));
        if (blank(result.cloudName) || blank(result.apiKey) || result.timestamp <= 0
                || !("upload".equals(result.type) || "private".equals(result.type) || "authenticated".equals(result.type))
                || result.overwrite == null
                || !validSide(result.front, scanId, "front") || !validSide(result.back, scanId, "back")) {
            throw invalidResponse();
        }
        return result;
    }
    private static void validateScanId(String scanId) throws ScanApiException {
        try {
            if (!UUID.fromString(scanId).toString().equals(scanId)) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new ScanApiException(ScanApiException.Kind.INVALID_REQUEST, 0, "Identificador de scan inválido.");
        }
    }
    public ScanSessionResponse submit(ScanSessionRequest request) throws IOException {
        com.venussystem.venusmobile.repository.ScanSubmissionValidator.validate(request, true);
        ScanSessionResponse result = execute(request.firebaseUid, token -> api.submit(token, request));
        if (blank(result.id) || !request.scanId.equals(result.scanId) || blank(result.status))
            throw invalidResponse();
        return result;
    }

    public UserResponse registerOwnAccount(String uid, String name, String email) throws IOException {
        if (blank(uid)) throw new ScanApiException(ScanApiException.Kind.AUTH, 0, "Entre novamente na sua conta.");
        UserRequest request = new UserRequest();
        request.firebaseUid = uid;
        request.name = blank(name) ? "Usuário Venus" : name.trim();
        request.email = blank(email) ? null : email.trim();
        request.status = "ACTIVE";
        UserResponse result = execute(uid, token -> api.registerAccount(token, request));
        if (!uid.equals(result.firebaseUid) || result.id == null || result.id <= 0) throw invalidResponse();
        if (!("ACTIVE".equals(result.status) || "PENDING".equals(result.status)))
            throw new ScanApiException(ScanApiException.Kind.FORBIDDEN, 403, "Sua conta precisa de uma verificação.");
        return result;
    }

    public ScanUploadSignaturesResponse connectAccountAndGetSignatures(
            String uid, String name, String email, String scanId) throws IOException {
        validateScanId(scanId);
        try {
            registerOwnAccount(uid, name, email);
        } catch (ScanApiException e) {
            if (e.httpCode != 409) throw e;
        }
        return signatures(uid, scanId);
    }
    private <T> T execute(String uid, Request<T> request) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            String bearer = session.bearerFor(uid, attempt == 1);
            Response<T> response;
            try {
                response = request.create(bearer).execute();
            } catch (SocketTimeoutException e) {
                throw new ScanApiException(ScanApiException.Kind.TIMEOUT, 0,
                        "A API demorou para responder. Aguarde um pouco e tente novamente.");
            } catch (IOException e) {
                throw new ScanApiException(ScanApiException.Kind.NETWORK, 0,
                        "Não foi possível conectar à API. Verifique a internet e tente novamente.");
            } catch (RuntimeException e) {
                throw invalidResponse();
            }
            if (response.isSuccessful() && response.body() != null) return response.body();
            String diagnostic = readSafeDiagnostic(response.errorBody());
            if (response.code() == 401 && attempt == 0) continue;
            if (response.code() == 401) throw new ScanApiException(ScanApiException.Kind.AUTH, 401,
                    "A sessão não foi aceita pela API (401). Entre novamente e tente outra vez.", diagnostic);
            if (response.code() == 403) throw new ScanApiException(ScanApiException.Kind.FORBIDDEN, 403,
                    "A API não autorizou o envio. A conta Firebase também precisa estar cadastrada e habilitada no backend (403).", diagnostic);
            if (response.isSuccessful()) throw invalidResponse();
            throw new ScanApiException(ScanApiException.Kind.HTTP, response.code(),
                    "A API não confirmou a operação (HTTP " + response.code() + "). Tente novamente mais tarde.", diagnostic);
        }
        throw new IOException("A sessão expirou. Entre novamente.");
    }

    private static String readSafeDiagnostic(ResponseBody body) {
        if (body == null) return null;
        try (ResponseBody closable = body) {
            String raw = closable.string();
            if (raw == null || raw.trim().isEmpty()) return null;
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) return null;
            JsonObject object = parsed.getAsJsonObject();
            List<String> parts = new ArrayList<>();
            addPrimitive(parts, object.get("message"));
            JsonElement details = object.get("details");
            if (details != null && details.isJsonArray()) {
                for (JsonElement detail : details.getAsJsonArray()) addPrimitive(parts, detail);
            }
            if (parts.isEmpty()) return null;
            String compact = String.join(" | ", parts)
                    .replaceAll("[\\r\\n\\t]+", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            return compact.length() <= 320 ? compact : compact.substring(0, 320);
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    private static void addPrimitive(List<String> parts, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return;
        String text = value.getAsString().trim();
        if (!text.isEmpty()) parts.add(text.length() <= 160 ? text : text.substring(0, 160));
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean validSide(ScanUploadSignaturesResponse.Signature side, String id, String name) {
        return side != null && ("scans/" + id + "/" + name).equals(side.publicId) && !blank(side.signature);
    }
    private static ScanApiException invalidResponse() {
        return new ScanApiException(ScanApiException.Kind.INVALID_RESPONSE, 0,
                "A API retornou uma resposta incompleta ou incompatível. As assinaturas não foram confirmadas.");
    }
    private interface Request<T> { Call<T> create(String bearer); }
}
