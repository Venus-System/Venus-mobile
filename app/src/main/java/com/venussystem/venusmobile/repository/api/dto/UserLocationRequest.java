package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do PATCH /api/users/{userId} com a localizacao aproximada da pessoa.
 *
 * Coordenadas arredondadas (cerca de 1 km) de proposito: a analise trabalha
 * por regiao. Cidade e estado ficam null quando o aparelho nao conseguiu
 * resolver o endereco, e o Gson nao escreve campo null.
 */
public class UserLocationRequest {
    public Double latitude;
    public Double longitude;
    public String city;
    public String state;
}
