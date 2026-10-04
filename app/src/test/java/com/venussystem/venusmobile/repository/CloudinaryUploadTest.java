package com.venussystem.venusmobile.repository;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.repository.api.CloudinaryUploadClient;
import com.venussystem.venusmobile.repository.api.ScanUploadException;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadedPhoto;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okio.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Synthetic files and intercepted HTTP only: never sends photos or credentials. */
public class CloudinaryUploadTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final long NOW = 1800000000L;
    private static final String ID = "7ff3ff2d-81cb-4968-973e-265ecacc073e";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private final List<Request> requests = new ArrayList<>();

    private ScanUploadSignaturesResponse signatures(String id) {
        ScanUploadSignaturesResponse s = new ScanUploadSignaturesResponse();
        s.cloudName = "test-cloud"; s.apiKey = "synthetic-key"; s.type = "authenticated";
        s.timestamp = NOW; s.overwrite = true;
        s.front = new ScanUploadSignaturesResponse.Signature();
        s.front.publicId = "scans/" + id + "/front"; s.front.signature = "synthetic-front-signature";
        s.back = new ScanUploadSignaturesResponse.Signature();
        s.back.publicId = "scans/" + id + "/back"; s.back.signature = "synthetic-back-signature";
        return s;
    }
    private ScanUploadedPhoto receipt(String id, String side) {
        ScanUploadedPhoto r = new ScanUploadedPhoto();
        r.cloudName = "test-cloud"; r.deliveryType = "authenticated"; r.version = NOW;
        r.image = new ScanSessionRequest.Image(); r.image.publicId = "scans/" + id + "/" + side;
        r.image.width = 800; r.image.height = 600; r.image.bytes = 1000L; r.image.format = "jpg";
        return r;
    }
    private String response() {
        return "{\"public_id\":\"scans/" + ID + "/front\",\"resource_type\":\"image\","
                + "\"type\":\"authenticated\",\"format\":\"jpg\",\"width\":800,\"height\":600,\"bytes\":1000,\"version\":1800000000}";
    }
    private CloudinaryUploadClient client(int code, String body) {
        return new CloudinaryUploadClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.add(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("Synthetic").body(ResponseBody.create(MediaType.get("application/json"), body)).build();
        }).build(), CLOCK);
    }
    private File photo() throws IOException {
        File f = temp.newFile(); Files.write(f.toPath(), new byte[]{1,2,3,4}); return f;
    }
    private ScanDraftStore.SavedDraft draft(ScanDraftStore store) throws IOException {
        ScanSubmissionDraft d = new ScanSubmissionDraft(); d.bindToUser("owner");
        d.setFrontPhotoPath(photo().getAbsolutePath()); d.setBackPhotoPath(photo().getAbsolutePath());
        // Minimal storage fixture; OCR parsing is covered by its own tests.
        d.setBackData(new Gson().fromJson("{\"fullText\":\"OCR preserved\",\"lines\":[],\"ingredients\":[],\"claims\":[]}", ScanBackData.class));
        return store.save("owner", d);
    }
    private ScanPhotoUploadCoordinator coordinator(ScanDraftStore store, ScanPhotoUploadCoordinator.Upload upload) {
        return new ScanPhotoUploadCoordinator(store, () -> { throw new IOException("Unexpected signature refresh"); }, () -> {}, upload);
    }

    @Test public void signedMultipartUsesFixedHostAndNoFirebaseCredentials() throws Exception {
        ScanUploadSignaturesResponse s = signatures(ID); s.uploadPreset = "preset";
        ScanUploadedPhoto r = client(200, response()).upload(s, ID, "front", photo());
        assertEquals("scans/" + ID + "/front", r.image.publicId);
        Request req = requests.get(0);
        assertEquals("https://api.cloudinary.com/v1_1/test-cloud/image/upload", req.url().toString());
        assertNull(req.header("Authorization"));
        Buffer buffer = new Buffer(); req.body().writeTo(buffer); String form = buffer.readUtf8();
        for (String field : Arrays.asList("api_key", "timestamp", "signature", "public_id", "type", "overwrite", "upload_preset", "file"))
            assertTrue(form.contains("name=\"" + field + "\""));
        assertTrue(form.contains("synthetic-front-signature")); assertFalse(form.contains("synthetic-back-signature"));
        assertFalse(form.contains("Bearer")); assertFalse(form.contains("api_secret"));
    }
    @Test public void optionalPresetIsOmittedAndFalseOverwriteIsPreserved() throws Exception {
        ScanUploadSignaturesResponse s = signatures(ID); s.overwrite = false;
        client(200, response()).upload(s, ID, "front", photo());
        Buffer b = new Buffer(); requests.get(0).body().writeTo(b); String form = b.readUtf8();
        assertFalse(form.contains("upload_preset")); assertTrue(form.contains("\r\n\r\nfalse\r\n"));
    }
    @Test public void rejectsExpiredSignatureBeforeHttp() throws Exception {
        ScanUploadSignaturesResponse s = signatures(ID); s.timestamp = NOW - 3601;
        assertEquals(ScanUploadException.Kind.EXPIRED_SIGNATURE,
                assertThrows(ScanUploadException.class, () -> client(200,response()).upload(s, ID, "front", photo())).kind);
        assertTrue(requests.isEmpty());
    }
    @Test public void rejectsUnexpectedPublicIdBeforeHttp() throws Exception {
        ScanUploadSignaturesResponse s = signatures(ID); s.front.publicId = "another-account/photo";
        assertEquals(ScanUploadException.Kind.CONFIGURATION,
                assertThrows(ScanUploadException.class, () -> client(200,response()).upload(s, ID,"front",photo())).kind);
        assertTrue(requests.isEmpty());
    }
    @Test public void rejectsMissingFileBeforeHttp() throws Exception {
        assertEquals(ScanUploadException.Kind.LOCAL, assertThrows(ScanUploadException.class,
                () -> client(200,response()).upload(signatures(ID),ID,"front",temp.newFile())).kind);
        assertTrue(requests.isEmpty());
    }
    @Test public void rejectsInvalidMetadataAndMalformedResponses() throws Exception {
        for (String body : Arrays.asList("null", "not-json", "{}", response().replace("/front", "/back"),
                response().replace("800", "0"), response().replace("authenticated", "upload"),
                response().replace("image", "video"), response().replace("1000", "null"))) {
            assertEquals(ScanUploadException.Kind.INVALID_RESPONSE, assertThrows(ScanUploadException.class,
                    () -> client(200,body).upload(signatures(ID),ID,"front",photo())).kind);
        }
    }
    @Test public void cloudinaryHttpErrorsRemainUploadErrors() throws Exception {
        for (int code : new int[]{401,403,429,500}) {
            ScanUploadException e = assertThrows(ScanUploadException.class,
                    () -> client(code,"secret server body").upload(signatures(ID),ID,"front",photo()));
            assertEquals(ScanUploadException.Kind.HTTP,e.kind); assertEquals(code,e.httpCode);
            assertFalse(e.getMessage().contains("secret"));
        }
    }
    @Test public void classifiesConnectionAndTimeoutWithoutLeakingDetails() throws Exception {
        for (IOException failure : new IOException[]{new IOException("secret"),new SocketTimeoutException("secret")}) {
            CloudinaryUploadClient c = new CloudinaryUploadClient(new OkHttpClient.Builder()
                    .addInterceptor(chain -> { throw failure; }).build(),CLOCK);
            ScanUploadException e = assertThrows(ScanUploadException.class, () -> c.upload(signatures(ID),ID,"front",photo()));
            assertEquals(failure instanceof SocketTimeoutException ? ScanUploadException.Kind.TIMEOUT : ScanUploadException.Kind.NETWORK,e.kind);
            assertFalse(e.getMessage().contains("secret"));
        }
    }
    @Test public void resumesOnlyBackAfterReopeningStoreAndPreservesSnapshot() throws Exception {
        Path root = temp.newFolder().toPath(); ScanDraftStore store = new ScanDraftStore(root);
        ScanDraftStore.SavedDraft saved = draft(store); String id = saved.draft.getScanId();
        List<String> sides = new ArrayList<>();
        ScanPhotoUploadCoordinator first = coordinator(store, (s,i,side,file) -> {
            sides.add(side); if (side.equals("back")) throw new IOException("Offline"); return receipt(i,side);
        });
        assertThrows(IOException.class, () -> first.run("owner",id,signatures(id),(e,s)->{}));
        assertNotNull(store.read("owner",id).frontUpload); assertNull(store.read("owner",id).backUpload);
        assertEquals("WAITING_UPLOAD",store.read("owner",id).state);
        ScanDraftStore reopened = new ScanDraftStore(root);
        ScanDraftStore.SavedDraft complete = coordinator(reopened,(s,i,side,file)->{
            sides.add(side); assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(file.toPath())); return receipt(i,side);
        }).run("owner",id,signatures(id),(e,s)->{});
        assertEquals(Arrays.asList("front","back","back"),sides);
        assertEquals("PHOTOS_UPLOADED",complete.state); assertEquals(1,reopened.pending("owner").size());
        String json = Files.readString(Path.of(complete.draft.getFrontPhotoPath()).resolveSibling("draft.json"));
        assertTrue(json.contains("OCR preserved")); assertFalse(json.contains("synthetic-key"));
        assertFalse(json.contains("synthetic-front-signature")); assertFalse(json.contains("Bearer"));
    }
    @Test public void bothConfirmedPhotosNeedNoUploadsOrNewSignatures() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        store.recordUpload("owner",id,"front",receipt(id,"front")); store.recordUpload("owner",id,"back",receipt(id,"back"));
        ScanDraftStore.SavedDraft result = coordinator(store,(s,i,side,file)->{ throw new AssertionError("Unexpected upload"); })
                .run("owner",id,null,(e,s)->assertEquals("SKIPPED",e));
        assertEquals("PHOTOS_UPLOADED",result.state);
    }
    @Test public void expiredSignatureIsRefreshedOnceAndThenContinues() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        AtomicInteger refresh = new AtomicInteger(), uploads = new AtomicInteger();
        new ScanPhotoUploadCoordinator(store,()->{refresh.incrementAndGet();return signatures(id);},()->{},(s,i,side,file)->{
            if (uploads.getAndIncrement()==0) throw new ScanUploadException(ScanUploadException.Kind.EXPIRED_SIGNATURE,0,"Expired");
            return receipt(i,side);
        }).run("owner",id,signatures(id),(e,s)->{});
        assertEquals(1,refresh.get()); assertEquals(3,uploads.get());
    }
    @Test public void repeatedExpiryDoesNotLoopOrPersistSuccess() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        AtomicInteger refresh = new AtomicInteger(), uploads = new AtomicInteger();
        ScanPhotoUploadCoordinator c = new ScanPhotoUploadCoordinator(store,()->{refresh.incrementAndGet();return signatures(id);},()->{},(s,i,side,file)->{
            uploads.incrementAndGet(); throw new ScanUploadException(ScanUploadException.Kind.EXPIRED_SIGNATURE,0,"Expired");
        });
        assertThrows(ScanUploadException.class,()->c.run("owner",id,signatures(id),(e,s)->{}));
        assertEquals(1,refresh.get()); assertEquals(2,uploads.get()); assertNull(store.read("owner",id).frontUpload);
    }
    @Test public void logoutDuringFrontUploadRecordsReceiptButStopsBack() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        AtomicInteger uploads = new AtomicInteger();
        ScanPhotoUploadCoordinator c = new ScanPhotoUploadCoordinator(store,()->signatures(id),()->{
            if (uploads.get()>0) throw new IOException("Signed out");
        },(s,i,side,file)->{uploads.incrementAndGet();return receipt(i,side);});
        assertThrows(IOException.class,()->c.run("owner",id,signatures(id),(e,s)->{}));
        assertEquals(1,uploads.get()); assertNotNull(store.read("owner",id).frontUpload); assertNull(store.read("owner",id).backUpload);
    }
    @Test public void rejectsDifferentOwnerBeforeUpload() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        assertThrows(IOException.class,()->coordinator(store,(s,i,side,file)->{throw new AssertionError();})
                .run("other-user",id,signatures(id),(e,s)->{}));
        assertThrows(IOException.class,()->store.recordUpload("other-user",id,"front",receipt(id,"front")));
    }
    @Test public void rejectsChangedCloudDestinationBeforeSendingSecondPhoto() throws Exception {
        ScanDraftStore store = new ScanDraftStore(temp.newFolder().toPath()); String id = draft(store).draft.getScanId();
        store.recordUpload("owner",id,"front",receipt(id,"front"));
        ScanUploadSignaturesResponse s = signatures(id); s.cloudName = "different-cloud";
        assertThrows(ScanUploadException.class,()->coordinator(store,(sig,i,side,file)->{throw new AssertionError();})
                .run("owner",id,s,(e,side)->{}));
        assertNull(store.read("owner",id).backUpload);
    }
}
