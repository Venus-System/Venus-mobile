package com.venussystem.venusmobile.repository.banco;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A conversa com a Venus de cada conta: uma linha por UID. */
@Entity(tableName = "conversa")
public class ConversaEntity {

    @PrimaryKey
    @NonNull
    public String uid;

    /** Null antes da primeira resposta: a API escolhe o id. */
    @Nullable
    @ColumnInfo(name = "conversation_id")
    public String conversationId;

    public ConversaEntity(@NonNull String uid, @Nullable String conversationId) {
        this.uid = uid;
        this.conversationId = conversationId;
    }
}
