package com.venussystem.venusmobile.repository.api;

import java.io.IOException;

/** Implementations must refuse credentials after logout or a change of account. */
public interface ScanAuthSession {
    String bearerFor(String expectedUid, boolean forceRefresh) throws IOException;
}
