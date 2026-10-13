package com.venussystem.venusmobile.repository.api.dto;

/**
 * Lista da pessoa na API. O id e o que o app guarda para renomear, apagar e
 * mexer nos produtos dela depois.
 *
 * coverKey e a capa padrao (FAVORITOS, ESCANEADOS ou SKINCARE) e coverUrl o
 * link da foto de capa; os dois vem null quando a lista nao tem.
 */
public class UserListResponse {
    public Long id;
    public String name;
    public String description;
    public String coverKey;
    public String coverUrl;
}
