package com.venussystem.venusmobile.repository.api;

import android.os.Looper;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Worker-thread only. Tokens are neither persisted nor logged. */
public final class FirebaseScanAuthSession implements ScanAuthSession {
    @Override public String bearerFor(String expectedUid, boolean forceRefresh) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("A autenticação de rede deve executar fora da tela principal.");
        FirebaseUser user = checkedUser(expectedUid);
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(forceRefresh), 30, TimeUnit.SECONDS);
            checkedUser(expectedUid);
            if (result.getToken() == null || result.getToken().isEmpty())
                throw new ScanApiException(ScanApiException.Kind.AUTH, 0,
                        "Não foi possível autenticar o envio. Entre novamente.");
            return "Bearer " + result.getToken();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Autenticação interrompida.", e);
        } catch (TimeoutException e) {
            throw new ScanApiException(ScanApiException.Kind.TIMEOUT, 0,
                    "A autenticação demorou para responder. Verifique a conexão e tente novamente.");
        } catch (ExecutionException e) {
            throw new ScanApiException(ScanApiException.Kind.AUTH, 0,
                    "Não foi possível obter a sessão Firebase. Verifique a conexão e o login.");
        }
    }
    public void requireSameUser(String uid) throws IOException { checkedUser(uid); }
    private FirebaseUser checkedUser(String uid) throws IOException {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (uid == null || current == null || !uid.equals(current.getUid()))
            throw new ScanApiException(ScanApiException.Kind.AUTH, 0,
                    "Entre com a conta que iniciou esta submissão.");
        return current;
    }
}
