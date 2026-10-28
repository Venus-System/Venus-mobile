package com.venussystem.venusmobile.repository.banco;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Uma mensagem da conversa com a Venus. O id cresce a cada insercao, entao
 * ordenar por ele da a ordem da conversa.
 */
@Entity(tableName = "mensagem", indices = {@Index("uid")})
public class MensagemEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String uid;

    /** Mensagem.DO_USUARIO ou Mensagem.DO_ASSISTENTE. */
    public int autor;

    @NonNull
    public String texto;

    public MensagemEntity(@NonNull String uid, int autor, @NonNull String texto) {
        this.uid = uid;
        this.autor = autor;
        this.texto = texto;
    }
}
