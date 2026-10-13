package com.venussystem.venusmobile.repository.api.dto;

import java.util.List;

/**
 * As rotas /search e as listagens por usuario da API devolvem um Slice do
 * Spring: os itens vem dentro de "content", e "last" diz se ha outra pagina.
 */
public class FatiaResponse<T> {
    public List<T> content;
    public Boolean last;
}
