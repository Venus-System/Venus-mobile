package com.venussystem.venusmobile.repository.api;

import java.io.IOException;

public interface ScanAuthSession {
    String bearerFor(String expectedUid, boolean forceRefresh) throws IOException;
}
