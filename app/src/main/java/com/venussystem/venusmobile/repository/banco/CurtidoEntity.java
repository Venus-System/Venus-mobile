package com.venussystem.venusmobile.repository.banco;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * Um produto numa das listas de curtidos de uma conta.
 *
 * Sao tres listas (ver CurtidosRepository): os curtidos da tela e as filas do
 * que falta curtir e descurtir na API. O mesmo produto pode estar em duas
 * delas ao mesmo tempo (curtido e ainda nao enviado), por isso a lista faz
 * parte da chave. A posicao guarda a ordem dentro da lista.
 */
@Entity(tableName = "curtido", primaryKeys = {"uid", "lista", "produto_id"})
public class CurtidoEntity {

    public static final String PRODUTOS = "produtos";
    public static final String PARA_CURTIR = "para_curtir";
    public static final String PARA_DESCURTIR = "para_descurtir";

    @NonNull
    public String uid;

    @NonNull
    public String lista;

    @ColumnInfo(name = "produto_id")
    public long produtoId;

    public int posicao;

    public CurtidoEntity(@NonNull String uid, @NonNull String lista, long produtoId, int posicao) {
        this.uid = uid;
        this.lista = lista;
        this.produtoId = produtoId;
        this.posicao = posicao;
    }
}
