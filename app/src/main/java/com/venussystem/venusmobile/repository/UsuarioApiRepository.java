package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.UserRequest;
import com.venussystem.venusmobile.repository.api.dto.UserResponse;

import java.io.IOException;

import retrofit2.Response;

/**
 * Descobre o id numerico da pessoa no Venus-CRUD a partir do UID do Firebase,
 * criando o cadastro na API na primeira vez.
 *
 * Tudo que o app grava na API (perfil, preferencias e, depois, listas e
 * favoritos) e por esse id, e a API de IA tambem precisa dele para consultar
 * os dados da pessoa. O Firebase so conhece o UID.
 *
 * O id fica guardado no aparelho por UID: ele nao muda, entao so a primeira
 * chamada vai na rede, e trocar de conta nunca reaproveita o id de outra pessoa.
 */
public class UsuarioApiRepository {

    private static final String ARQUIVO = "venus_usuario_api";
    private static final String PREFIXO_ID = "id_";

    private static final String STATUS_ATIVO = "ACTIVE";
    private static final String NOME_PADRAO = "Usuário Venus";

    // Estatica porque cada tela cria o seu repositorio: sem ela, duas telas
    // abrindo juntas no primeiro acesso cadastravam a mesma pessoa duas vezes.
    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    private final VenusApi api;
    private final SessaoUsuario sessao;

    public UsuarioApiRepository(Context context) {
        this(context, ClienteApi.get(), new SessaoFirebase());
    }

    @VisibleForTesting
    public UsuarioApiRepository(Context context, VenusApi api, SessaoUsuario sessao) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        this.api = api;
        this.sessao = sessao;
    }

    /**
     * O id da pessoa logada na API, buscando ou criando o cadastro se ainda nao
     * estiver guardado.
     *
     * @return null se ninguem esta logado ou se a API nao respondeu - quem chama
     * tenta de novo numa proxima vez.
     */
    @WorkerThread
    @Nullable
    public Long obterIdSincrono() {
        String uid = sessao.uid();
        if (uid == null) {
            return null;
        }

        synchronized (TRAVA) {
            Long salvo = idSalvo(uid);
            if (salvo != null) {
                return salvo;
            }

            try {
                Long id = buscarPorUid(uid);
                if (id == null) {
                    id = criar(uid);
                }
                if (id != null) {
                    prefs.edit().putLong(PREFIXO_ID + uid, id).apply();
                }
                return id;
            } catch (IOException | FalhaApi e) {
                return null;
            }
        }
    }

    /**
     * So o que ja esta guardado, sem rede. O chat usa este: esperar o cadastro
     * ali somaria o cold start do Venus-CRUD ao tempo de resposta da Venus.
     */
    @Nullable
    public Long idEmCache() {
        String uid = sessao.uid();
        return uid == null ? null : idSalvo(uid);
    }

    /** UID da sessão atual, usado para ler dados locais da mesma conta. */
    @Nullable
    public String uidAtual() {
        return sessao.uid();
    }

    /**
     * Descarta o id guardado da pessoa logada. Serve para quando a API mostra
     * que ele nao existe mais (banco recriado): a proxima chamada busca de novo.
     */
    public void esquecerId() {
        String uid = sessao.uid();
        if (uid != null) {
            prefs.edit().remove(PREFIXO_ID + uid).apply();
        }
    }

    @Nullable
    private Long idSalvo(String uid) {
        String chave = PREFIXO_ID + uid;
        return prefs.contains(chave) ? prefs.getLong(chave, 0L) : null;
    }

    /**
     * @return o id, ou null se a API confirmou que a pessoa ainda nao existe.
     * @throws FalhaApi se a API respondeu erro: ai nao da para saber se existe,
     *                  e criar as cegas poderia duplicar o cadastro.
     */
    @Nullable
    private Long buscarPorUid(String uid) throws IOException, FalhaApi {
        Response<FatiaResponse<UserResponse>> resposta = api.buscarUsuarioPorUid(uid).execute();
        if (!resposta.isSuccessful()) {
            throw new FalhaApi();
        }

        FatiaResponse<UserResponse> fatia = resposta.body();
        if (fatia == null || fatia.content == null) {
            return null;
        }
        // Confere o UID em vez de pegar o primeiro da lista: a busca e um filtro
        // generico da API, e o id de outra pessoa aqui seria o pior erro possivel.
        for (UserResponse usuario : fatia.content) {
            if (uid.equals(usuario.firebaseUid) && usuario.id != null) {
                return usuario.id;
            }
        }
        return null;
    }

    @Nullable
    private Long criar(String uid) throws IOException, FalhaApi {
        UserRequest corpo = new UserRequest();
        corpo.firebaseUid = uid;
        corpo.name = nomeParaCadastro();
        corpo.email = sessao.email();
        corpo.status = STATUS_ATIVO;

        Response<UserResponse> resposta = api.criarUsuario(corpo).execute();
        if (resposta.isSuccessful() && resposta.body() != null) {
            return resposta.body().id;
        }
        // 409: alguem cadastrou antes - o site do Venus usa o mesmo Firebase, e
        // o mesmo UID pode ja ter entrado por la. Basta buscar de novo.
        if (resposta.code() == 409) {
            return buscarPorUid(uid);
        }
        throw new FalhaApi();
    }

    /**
     * A API exige um nome. No cadastro por e-mail o app grava o nome no
     * Firebase, e no Google ele ja vem; o resto e so para nunca mandar vazio.
     */
    private String nomeParaCadastro() {
        String nome = sessao.nome();
        if (nome != null && !nome.trim().isEmpty()) {
            return nome.trim();
        }
        String email = sessao.email();
        int arroba = email == null ? -1 : email.indexOf('@');
        if (arroba > 0) {
            return email.substring(0, arroba);
        }
        return NOME_PADRAO;
    }

    private static class FalhaApi extends Exception {
    }
}
