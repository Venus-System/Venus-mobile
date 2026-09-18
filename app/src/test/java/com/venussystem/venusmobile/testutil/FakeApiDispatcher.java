package com.venussystem.venusmobile.testutil;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.NonNull;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public final class FakeApiDispatcher extends Dispatcher {

    private final Map<String, MockResponse> respostas = new LinkedHashMap<>();

    public FakeApiDispatcher em(String path, MockResponse resposta) {
        respostas.put(path, resposta);
        return this;
    }

    @NonNull
    @Override
    public MockResponse dispatch(@NonNull RecordedRequest request) {
        MockResponse resposta = respostas.get(request.getPath());
        if (resposta == null) {
            return new MockResponse().setResponseCode(404);
        }
        return resposta;
    }
}
