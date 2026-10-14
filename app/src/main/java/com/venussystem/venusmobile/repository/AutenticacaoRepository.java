package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.venussystem.venusmobile.model.ResultadoAuth;
import com.venussystem.venusmobile.model.Usuario;

public class AutenticacaoRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public LiveData<ResultadoAuth> cadastrar(String nome, String email, String senha) {
        MutableLiveData<ResultadoAuth> resultado = new MutableLiveData<>();

        auth.createUserWithEmailAndPassword(email, senha).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                resultado.setValue(ResultadoAuth.erro(traduzirErro(task.getException())));
                return;
            }

            FirebaseUser usuario = auth.getCurrentUser();
            if (usuario == null) {
                resultado.setValue(ResultadoAuth.sucesso());
                return;
            }
            UserProfileChangeRequest perfil = new UserProfileChangeRequest.Builder()
                    .setDisplayName(nome)
                    .build();

            usuario.updateProfile(perfil)
                    .addOnCompleteListener(t -> resultado.setValue(ResultadoAuth.sucesso()));
        });

        return resultado;
    }

    public LiveData<ResultadoAuth> entrar(String email, String senha) {
        MutableLiveData<ResultadoAuth> resultado = new MutableLiveData<>();

        auth.signInWithEmailAndPassword(email, senha).addOnCompleteListener(task ->
                resultado.setValue(task.isSuccessful()
                        ? ResultadoAuth.sucesso()
                        : ResultadoAuth.erro(traduzirErro(task.getException()))));

        return resultado;
    }

    public LiveData<ResultadoAuth> recuperarSenha(String email) {
        MutableLiveData<ResultadoAuth> resultado = new MutableLiveData<>();

        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                resultado.setValue(ResultadoAuth.sucesso());
                return;
            }

            if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                resultado.setValue(ResultadoAuth.sucesso());
                return;
            }
            resultado.setValue(ResultadoAuth.erro(traduzirErro(task.getException())));
        });

        return resultado;
    }

    
    public LiveData<ResultadoAuth> entrarComGoogle(String idTokenGoogle) {
        MutableLiveData<ResultadoAuth> resultado = new MutableLiveData<>();

        AuthCredential credencial = GoogleAuthProvider.getCredential(idTokenGoogle, null);
        auth.signInWithCredential(credencial).addOnCompleteListener(task ->
                resultado.setValue(task.isSuccessful()
                        ? ResultadoAuth.sucesso()
                        : ResultadoAuth.erro(traduzirErro(task.getException()))));

        return resultado;
    }

    /**
     * Sai da conta. Antes, apaga a conversa com a Venus guardada no aparelho:
     * ela costuma ter pele, alergias e saude, e nao deve ficar para tras. O
     * resto (questionario, listas) fica guardado por conta, ver DadosDaConta.
     */
    public void sair(Context context) {
        new ConversaSalvaRepository(context).apagar();
        auth.signOut();
    }

    
    public Usuario usuarioLogado() {
        FirebaseUser usuario = auth.getCurrentUser();
        if (usuario == null) {
            return null;
        }
        return new Usuario(usuario.getUid(), usuario.getDisplayName(), usuario.getEmail());
    }

    public boolean temSessaoAtiva() {
        return auth.getCurrentUser() != null;
    }

    private String traduzirErro(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Senha muito fraca. Use no mínimo 8 caracteres.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Este email já está cadastrado.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "Não encontramos uma conta com este email.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Email ou senha incorretos.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "Sem conexão. Verifique sua internet.";
        }
        return "Não foi possível concluir. Tente novamente.";
    }
}
