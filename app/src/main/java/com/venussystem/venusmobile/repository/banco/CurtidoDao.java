package com.venussystem.venusmobile.repository.banco;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CurtidoDao {

    @Query("SELECT produto_id FROM curtido WHERE uid = :uid AND lista = :lista ORDER BY posicao")
    List<Long> ids(String uid, String lista);

    @Query("SELECT COUNT(*) FROM curtido WHERE uid = :uid")
    int quantos(String uid);

    @Query("DELETE FROM curtido WHERE uid = :uid AND lista = :lista")
    void apagarLista(String uid, String lista);

    @Insert
    void inserir(List<CurtidoEntity> curtidos);
}
