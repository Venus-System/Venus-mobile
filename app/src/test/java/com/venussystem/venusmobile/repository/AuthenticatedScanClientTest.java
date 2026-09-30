package com.venussystem.venusmobile.repository;

import static org.junit.Assert.*;
import com.venussystem.venusmobile.repository.api.*;
import com.venussystem.venusmobile.repository.api.dto.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthenticatedScanClientTest {
    private static final String SCAN_ID = "35fbadba-264b-4fbc-bcbe-da491aa3418d";
    @Test public void sendsFirebaseBearerAndPreservesIdOnRefresh() throws Exception {
        FakeApi api = new FakeApi(401, 201);
        List<Boolean> refreshes = new ArrayList<>();
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> {
            assertEquals("user-a", uid); refreshes.add(refresh); return "Bearer test-" + refresh;
        });
        ScanSessionRequest r = new ScanSessionRequest(); r.firebaseUid = "user-a"; r.scanId = "stable-id";
        assertEquals("stable-id", client.submit(r).scanId);
        assertEquals(java.util.Arrays.asList(false, true), refreshes);
        assertEquals(java.util.Arrays.asList("Bearer test-false", "Bearer test-true"), api.tokens);
        assertSame(api.requests.get(0), api.requests.get(1));
    }
    @Test public void forbiddenDoesNotLoopOrTryToCreateAnotherAccount() {
        FakeApi api = new FakeApi(403);
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> "Bearer test");
        IOException error = assertThrows(IOException.class, () -> client.signatures("user-a", SCAN_ID));
        assertTrue(error.getMessage().contains("403"));
        assertEquals(1, api.calls);
    }
    @Test public void expiredSessionIsRetriedOnlyOnce() {
        FakeApi api = new FakeApi(401, 401);
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> "Bearer test");
        ScanApiException error = assertThrows(ScanApiException.class, () -> client.signatures("user-a", SCAN_ID));
        assertEquals(ScanApiException.Kind.AUTH, error.kind);
        assertEquals(2, api.calls);
    }
    @Test public void logoutPreventsNetworkRequest() {
        FakeApi api = new FakeApi(200);
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> {
            throw new IOException("Conta alterada");
        });
        assertThrows(IOException.class, () -> client.signatures("user-a", SCAN_ID));
        assertEquals(0, api.calls);
    }
    @Test public void serverFailureIsNotReportedAsSuccess() {
        FakeApi api = new FakeApi(503);
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> "Bearer test");
        assertThrows(IOException.class, () -> client.signatures("user-a", SCAN_ID));
        assertEquals(1, api.calls);
    }
    @Test public void signaturesUseBearerAndStableScanIdAfter401() throws Exception {
        FakeApi api = new FakeApi(401, 200);
        List<Boolean> refreshes = new ArrayList<>();
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> {
            assertEquals("user-a", uid);
            refreshes.add(refresh);
            return "Bearer test-" + refresh;
        });
        ScanUploadSignaturesResponse response = client.signatures("user-a", SCAN_ID);
        assertEquals("scans/" + SCAN_ID + "/front", response.front.publicId);
        assertEquals("scans/" + SCAN_ID + "/back", response.back.publicId);
        assertEquals(java.util.Arrays.asList(SCAN_ID, SCAN_ID), api.scanIds);
        assertEquals(java.util.Arrays.asList(false, true), refreshes);
        assertEquals(java.util.Arrays.asList("Bearer test-false", "Bearer test-true"), api.tokens);
        assertTrue(api.requests.isEmpty()); // No POST is performed.
    }
    @Test public void incompleteSignaturesAreRejected() {
        FakeApi api = new FakeApi(200); api.signatureBody.back = null;
        assertInvalidResponse(api);
    }
    @Test public void signaturesForAnotherScanAreRejected() {
        FakeApi api = new FakeApi(200); api.signatureBody.front.publicId = "scans/other/front";
        assertInvalidResponse(api);
    }
    @Test public void swappedSidesAreRejected() {
        FakeApi api = new FakeApi(200); api.signatureBody.back.publicId = api.signatureBody.front.publicId;
        assertInvalidResponse(api);
    }
    @Test public void missingSignedParameterIsRejected() {
        FakeApi api = new FakeApi(200); api.signatureBody.overwrite = null;
        assertInvalidResponse(api);
    }
    @Test public void requiredResponseFieldsCannotBeEmpty() {
        FakeApi noCloud = new FakeApi(200); noCloud.signatureBody.cloudName = " ";
        assertInvalidResponse(noCloud);
        FakeApi noKey = new FakeApi(200); noKey.signatureBody.apiKey = null;
        assertInvalidResponse(noKey);
        FakeApi noTimestamp = new FakeApi(200); noTimestamp.signatureBody.timestamp = 0;
        assertInvalidResponse(noTimestamp);
        FakeApi noType = new FakeApi(200); noType.signatureBody.type = null;
        assertInvalidResponse(noType);
        FakeApi noSignature = new FakeApi(200); noSignature.signatureBody.back.signature = "";
        assertInvalidResponse(noSignature);
    }
    @Test public void accountChangeDuringRefreshStopsSecondRequest() {
        FakeApi api = new FakeApi(401, 200);
        AuthenticatedScanClient client = new AuthenticatedScanClient(api, (uid, refresh) -> {
            if (refresh) throw new ScanApiException(ScanApiException.Kind.AUTH, 0, "Conta alterada");
            return "Bearer test";
        });
        assertThrows(ScanApiException.class, () -> client.signatures("user-a", SCAN_ID));
        assertEquals(1, api.calls);
    }
    @Test public void emptySuccessfulResponseIsRejected() {
        FakeApi api = new FakeApi(200); api.signatureBody = null;
        assertInvalidResponse(api);
    }
    @Test public void optionalPresetCanBeAbsent() throws Exception {
        FakeApi api = new FakeApi(200); api.signatureBody.uploadPreset = null;
        assertNotNull(client(api).signatures("user-a", SCAN_ID));
    }
    @Test public void invalidScanIdNeverReachesNetwork() {
        FakeApi api = new FakeApi(200);
        ScanApiException error = assertThrows(ScanApiException.class,
                () -> client(api).signatures("user-a", "invalid"));
        assertEquals(ScanApiException.Kind.INVALID_REQUEST, error.kind);
        assertEquals(0, api.calls);
    }
    @Test public void networkFailureIsSafeAndNotAutomaticallyRetried() {
        FakeApi api = new FakeApi(200); api.failure = new java.net.UnknownHostException("sensitive details");
        ScanApiException error = assertThrows(ScanApiException.class,
                () -> client(api).signatures("user-a", SCAN_ID));
        assertEquals(ScanApiException.Kind.NETWORK, error.kind);
        assertFalse(error.getMessage().contains("sensitive"));
        assertEquals(1, api.calls);
    }
    @Test public void timeoutHasActionableMessage() {
        FakeApi api = new FakeApi(200); api.failure = new java.net.SocketTimeoutException();
        ScanApiException error = assertThrows(ScanApiException.class,
                () -> client(api).signatures("user-a", SCAN_ID));
        assertEquals(ScanApiException.Kind.TIMEOUT, error.kind);
        assertEquals(1, api.calls);
    }
    @Test public void manualRetryKeepsSameScanId() throws Exception {
        FakeApi api = new FakeApi(503, 200);
        AuthenticatedScanClient client = client(api);
        assertThrows(IOException.class, () -> client.signatures("user-a", SCAN_ID));
        assertNotNull(client.signatures("user-a", SCAN_ID));
        assertEquals(java.util.Arrays.asList(SCAN_ID, SCAN_ID), api.scanIds);
        assertTrue(api.requests.isEmpty());
    }
    private static AuthenticatedScanClient client(FakeApi api) {
        return new AuthenticatedScanClient(api, (uid, refresh) -> "Bearer test");
    }
    private static void assertInvalidResponse(FakeApi api) {
        ScanApiException error = assertThrows(ScanApiException.class,
                () -> client(api).signatures("user-a", SCAN_ID));
        assertEquals(ScanApiException.Kind.INVALID_RESPONSE, error.kind);
    }
    private static ScanUploadSignaturesResponse validSignatures() {
        ScanUploadSignaturesResponse r = new ScanUploadSignaturesResponse();
        r.cloudName = "test-cloud"; r.apiKey = "test-public-key"; r.timestamp = 1790766000L;
        r.type = "upload"; r.overwrite = true;
        r.front = new ScanUploadSignaturesResponse.Signature();
        r.front.publicId = "scans/" + SCAN_ID + "/front"; r.front.signature = "front-test-signature";
        r.back = new ScanUploadSignaturesResponse.Signature();
        r.back.publicId = "scans/" + SCAN_ID + "/back"; r.back.signature = "back-test-signature";
        return r;
    }
    private static final class FakeApi implements ScanSubmissionApi {
        final int[] codes;
        int calls;
        List<String> tokens = new ArrayList<>();
        List<ScanSessionRequest> requests = new ArrayList<>();
        List<String> scanIds = new ArrayList<>();
        ScanUploadSignaturesResponse signatureBody = validSignatures();
        IOException failure;
        FakeApi(int... codes) { this.codes = codes; }
        @Override public Call<ScanUploadSignaturesResponse> signatures(String bearer, String id) {
            tokens.add(bearer); scanIds.add(id); return call(signatureBody);
        }
        @Override public Call<ScanSessionResponse> submit(String bearer, ScanSessionRequest r) {
            tokens.add(bearer); requests.add(r);
            ScanSessionResponse response = new ScanSessionResponse(); response.scanId = r.scanId;
            return call(response);
        }
        private <T> Call<T> call(T body) {
            int code = codes[calls++];
            Response<T> response = code >= 200 && code < 300 ? Response.success(body)
                    : Response.error(code, ResponseBody.create(null, "{}"));
            return new Call<T>() {
                @Override public Response<T> execute() throws IOException {
                    if (failure != null) throw failure;
                    return response;
                }
                @Override public void enqueue(Callback<T> c) { throw new UnsupportedOperationException(); }
                @Override public boolean isExecuted() { return false; }
                @Override public void cancel() { }
                @Override public boolean isCanceled() { return false; }
                @Override public Call<T> clone() { throw new UnsupportedOperationException(); }
                @Override public Request request() { throw new UnsupportedOperationException(); }
                @Override public Timeout timeout() { return Timeout.NONE; }
            };
        }
    }
}
