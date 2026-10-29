package com.venussystem.venusmobile.repository.banco;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConversaDao {

    @Nullable
    @Query("SELECT * FROM conversa WHERE uid = :uid")
    ConversaEntity conversa(String uid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void gravarConversa(ConversaEntity conversa);

    @Query("SELECT * FROM mensagem WHERE uid = :uid ORDER BY id")
    List<MensagemEntity> mensagens(String uid);

    @Insert
    void inserirMensagens(List<MensagemEntity> mensagens);

    /** Deixa so as `limite` mensagens mais recentes da conta. */
    @Query("DELETE FROM mensagem WHERE uid = :uid AND id NOT IN "
            + "(SELECT id FROM mensagem WHERE uid = :uid ORDER BY id DESC LIMIT :limite)")
    void manterSoAsUltimas(String uid, int limite);

    @Query("DELETE FROM mensagem WHERE uid = :uid")
    void apagarMensagens(String uid);

    @Query("DELETE FROM conversa WHERE uid = :uid")
    void apagarConversa(String uid);
}
