package com.venussystem.venusmobile.repository.api.dto;

/**
 * Item de GET /api/profile-tags/preferences: as etiquetas de preferencia que a
 * pessoa pode ter. As preferencias da tela que nao tem campo proprio em
 * /api/user-preferences sao gravadas como uma destas etiquetas.
 */
public class PreferenceCatalogResponse {
    public Long id;
    public String slug;
    public String name;
}
