package com.venussystem.venusmobile.repository.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PerfilDaApi {
    public final Map<String, String> tags = new HashMap<>();
    public final Map<String, List<String>> listas = new HashMap<>();
    public final Set<Long> alergiasNaApi = new HashSet<>();
    public final Set<Long> etiquetasNaApi = new HashSet<>();
}
