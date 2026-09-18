package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Listas do proprio usuario, guardadas no aparelho: a API tem /api/user-lists,
 * mas depende de um userId que o app ainda nao sabe obter (ver
 * DetalheListaActivity). Isto e o cache local ate essa integracao existir.
 */
public class ColecaoRepository {

    private static final String ARQUIVO = "venus_listas";
    private static final String CHAVE_LISTAS = "minhas_listas";

    // Uma instalacao nova comeca com estas 3, como ponto de partida. Depois
    // disso quem manda e o que estiver salvo (criar, editar capa, e no futuro
    // editar/excluir).
    private static final List<ColecaoSalva> EXEMPLO = Arrays.asList(
            new ColecaoSalva(1L, "Produtos favoritados", null, "favoritos", null),
            new ColecaoSalva(2L, "Produtos escaneados", null, "escaneados", null),
            new ColecaoSalva(3L, "Rotina de skincare", null, "skincare", null)
    );

    private static final Type TIPO_LISTA_SALVA = new TypeToken<List<ColecaoSalva>>() {
    }.getType();

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ColecaoRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    public LiveData<List<Colecao>> minhasListas() {
        return new MutableLiveData<>(paraColecoes(carregar()));
    }

    public Colecao criar(String nome, String descricao, @Nullable String caminhoImagem) {
        List<ColecaoSalva> atuais = carregar();
        ColecaoSalva nova = new ColecaoSalva(
                proximoId(atuais), nome, descricao, null, caminhoImagem);

        List<ColecaoSalva> atualizadas = new ArrayList<>();
        atualizadas.add(nova);
        atualizadas.addAll(atuais);
        salvar(atualizadas);

        return nova.paraColecao();
    }

    /**
     * Troca a capa de uma lista ja existente (a de exemplo ou uma criada).
     * Silenciosamente nao faz nada se o id nao existir mais - a tela que
     * chamou ja teria fechado nesse caso.
     */
    public void atualizarImagem(long id, String caminhoImagem) {
        List<ColecaoSalva> atuais = carregar();
        List<ColecaoSalva> atualizadas = new ArrayList<>();
        for (ColecaoSalva c : atuais) {
            atualizadas.add(c.id == id
                    ? new ColecaoSalva(c.id, c.nome, c.descricao, c.chaveImagem, caminhoImagem)
                    : c);
        }
        salvar(atualizadas);
    }

    public void renomear(long id, String novoNome) {
        List<ColecaoSalva> atuais = carregar();
        List<ColecaoSalva> atualizadas = new ArrayList<>();
        for (ColecaoSalva c : atuais) {
            atualizadas.add(c.id == id
                    ? new ColecaoSalva(c.id, novoNome, c.descricao, c.chaveImagem, c.caminhoImagem)
                    : c);
        }
        salvar(atualizadas);
    }

    public void atualizarDescricao(long id, String novaDescricao) {
        List<ColecaoSalva> atuais = carregar();
        List<ColecaoSalva> atualizadas = new ArrayList<>();
        for (ColecaoSalva c : atuais) {
            atualizadas.add(c.id == id
                    ? new ColecaoSalva(c.id, c.nome, novaDescricao, c.chaveImagem, c.caminhoImagem)
                    : c);
        }
        salvar(atualizadas);
    }

    public void excluir(long id) {
        List<ColecaoSalva> atuais = carregar();
        List<ColecaoSalva> atualizadas = new ArrayList<>();
        for (ColecaoSalva c : atuais) {
            if (c.id == id) {
                excluirArquivoDaCapa(c.caminhoImagem);
            } else {
                atualizadas.add(c);
            }
        }
        salvar(atualizadas);
    }

    /**
     * A capa de uma lista criada pelo usuario e um arquivo proprio do app (ver
     * ImagemLocalUtil); sem apagar aqui, ele ficaria orfao no armazenamento
     * depois que a lista some. As de exemplo usam drawable, entao nao tem
     * arquivo nenhum a apagar.
     */
    private void excluirArquivoDaCapa(@Nullable String caminhoImagem) {
        if (caminhoImagem == null) {
            return;
        }
        try {
            String caminho = Uri.parse(caminhoImagem).getPath();
            if (caminho != null) {
                new File(caminho).delete();
            }
        } catch (Exception ignorado) {
            // Falha ao limpar o arquivo nao pode impedir a lista de ser excluida.
        }
    }

    private List<ColecaoSalva> carregar() {
        String salvo = prefs.getString(CHAVE_LISTAS, null);
        if (salvo == null) {
            salvar(EXEMPLO);
            return new ArrayList<>(EXEMPLO);
        }
        List<ColecaoSalva> lista = gson.fromJson(salvo, TIPO_LISTA_SALVA);
        return lista == null ? new ArrayList<>() : lista;
    }

    private void salvar(List<ColecaoSalva> listas) {
        prefs.edit().putString(CHAVE_LISTAS, gson.toJson(listas)).apply();
    }

    private long proximoId(List<ColecaoSalva> atuais) {
        long maior = 0;
        for (ColecaoSalva c : atuais) {
            if (c.id > maior) {
                maior = c.id;
            }
        }
        return maior + 1;
    }

    private List<Colecao> paraColecoes(List<ColecaoSalva> salvas) {
        List<Colecao> lista = new ArrayList<>();
        for (ColecaoSalva s : salvas) {
            lista.add(s.paraColecao());
        }
        return lista;
    }

    /**
     * Formato gravado no SharedPreferences. Guarda a CHAVE do drawable de
     * exemplo, nao o id do resource: um resource id pode mudar entre builds,
     * e um valor salvo assim ficaria apontando pro drawable errado.
     */
    private static class ColecaoSalva {
        final long id;
        final String nome;
        final String descricao;
        final String chaveImagem;
        final String caminhoImagem;

        ColecaoSalva(long id, String nome, String descricao, String chaveImagem,
                     String caminhoImagem) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
            this.chaveImagem = chaveImagem;
            this.caminhoImagem = caminhoImagem;
        }

        Colecao paraColecao() {
            // Uma capa escolhida pelo usuario sempre vence o drawable de
            // exemplo - e como uma lista de exemplo troca de capa.
            if (caminhoImagem != null) {
                return new Colecao(id, nome, caminhoImagem, 0, descricao);
            }
            return new Colecao(id, nome, null, drawablePara(chaveImagem), descricao);
        }

        @DrawableRes
        private static int drawablePara(String chave) {
            if (chave == null) {
                return 0;
            }
            switch (chave) {
                case "favoritos":
                    return R.drawable.capa_favoritos;
                case "escaneados":
                    return R.drawable.capa_escaneados;
                case "skincare":
                    return R.drawable.capa_skincare;
                default:
                    return 0;
            }
        }
    }
}
