package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/users. O app nunca manda senha: quem autentica e o
 * Firebase, a API so guarda a pessoa para ligar perfil, listas etc. ao UID.
 */
public class UserRequest {
    public String firebaseUid;
    public String name;
    public String email;
    public String status;
}
