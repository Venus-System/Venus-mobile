package com.venussystem.venusmobile.repository.banco;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * O banco SQLite do aparelho (arquivo venus.db), feito com o Room.
 *
 * Guarda a conversa com a Venus e os produtos curtidos. Toda linha leva o UID
 * do Firebase, para cada conta do aparelho ver so o que e dela - o mesmo que o
 * DadosDaConta faz com os arquivos de preferencias.
 *
 * As consultas rodam na thread de quem chama, como as SharedPreferences que
 * vieram antes: os repositorios sao sincronos e as tabelas sao pequenas (ate
 * 100 mensagens e uma lista de ids).
 */
@Database(
        entities = {ConversaEntity.class, MensagemEntity.class, CurtidoEntity.class},
        version = 1)
public abstract class VenusDatabase extends RoomDatabase {

    static final String ARQUIVO = "venus.db";

    private static VenusDatabase instancia;
    private static Context aplicacaoDaInstancia;

    public abstract ConversaDao conversaDao();

    public abstract CurtidoDao curtidoDao();

    public static synchronized VenusDatabase get(Context context) {
        Context aplicacao = context.getApplicationContext();
        // No aparelho a aplicacao e sempre a mesma. Nos testes do Robolectric
        // cada teste tem a sua, com pastas proprias: o banco acompanha.
        if (instancia == null || aplicacaoDaInstancia != aplicacao) {
            instancia = Room.databaseBuilder(aplicacao, VenusDatabase.class, ARQUIVO)
                    .allowMainThreadQueries()
                    .build();
            aplicacaoDaInstancia = aplicacao;
        }
        return instancia;
    }
}
