package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.UserRequest;
import com.venussystem.venusmobile.repository.api.dto.UserResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** Separate from public catalogue calls; Firebase credentials never go to Cloudinary. */
public interface ScanSubmissionApi {
    /** Self-registration, invoked only after the user chooses to connect their account. */
    @POST("api/users")
    Call<UserResponse> registerAccount(@Header("Authorization") String bearer, @Body UserRequest request);

    @GET("api/scan-sessions/upload-signatures")
    Call<ScanUploadSignaturesResponse> signatures(@Header("Authorization") String bearer,
                                                 @Query("scanId") String scanId);

    @POST("api/scan-sessions")
    Call<ScanSessionResponse> submit(@Header("Authorization") String bearer,
                                     @Body ScanSessionRequest request);
}
