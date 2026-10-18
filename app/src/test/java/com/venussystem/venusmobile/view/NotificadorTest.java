package com.venussystem.venusmobile.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class NotificadorTest {

    private Application app;
    private NotificationManager gerente;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        gerente = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Notificador.criarCanais(app);
    }

    private List<Notification> publicadas() {
        return shadowOf(gerente).getAllNotifications();
    }

    @Test
    public void canais_sao_lembretes_envio_de_produtos_e_analise_de_produtos() {
        NotificationChannel lembretes = gerente.getNotificationChannel(Notificador.Canal.LEMBRETES.id);
        NotificationChannel analise = gerente.getNotificationChannel(Notificador.Canal.ANALISE_DE_PRODUTOS.id);
        NotificationChannel envio = gerente.getNotificationChannel(Notificador.Canal.ENVIO_DE_PRODUTOS.id);

        assertNotNull(lembretes);
        assertNotNull(analise);
        assertNotNull(envio);
        assertEquals("Lembretes", lembretes.getName().toString());
        assertEquals("Análise de produtos", analise.getName().toString());
        assertEquals("Envio de produtos", envio.getName().toString());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, analise.getImportance());
    }

    @Test
    public void criarCanais_duasVezesNaoDuplica() {
        Notificador.criarCanais(app);

        assertEquals(3, gerente.getNotificationChannels().size());
    }

    @Test
    public void semPermissao_naoPublicaENaoQuebra() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        boolean publicou = Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 1, "Titulo", "Texto");

        assertFalse(publicou);
        assertTrue(publicadas().isEmpty());
    }

    @Test
    public void comPermissao_publicaTituloETextoNoCanalCerto() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        boolean publicou = Notificador.mostrar(app, Notificador.Canal.ENVIO_DE_PRODUTOS, 5, "Produto enviado",
                "Hidratante foi enviado para análise.");

        assertTrue(publicou);
        Notification n = publicadas().get(0);
        assertEquals("Produto enviado", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Hidratante foi enviado para análise.",
                n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(Notificador.Canal.ENVIO_DE_PRODUTOS.id, n.getChannelId());
    }

    @Test
    public void tocar_abreATelaPorCimaDaPrincipal() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 1, "T", "X",
                new Intent(app, ProdutosEmAnaliseActivity.class));

        Intent[] pilha = shadowOf(publicadas().get(0).contentIntent).getSavedIntents();
        assertEquals(2, pilha.length);
        assertEquals(PrincipalActivity.class.getName(), pilha[0].getComponent().getClassName());
        assertEquals(ProdutosEmAnaliseActivity.class.getName(), pilha[1].getComponent().getClassName());
    }

    @Test
    public void mesmoId_trocaOAvisoEmVezDeEmpilhar() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 7, "Primeiro", "a");
        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 7, "Segundo", "b");

        assertEquals(1, publicadas().size());
        assertEquals("Segundo", publicadas().get(0).extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    }

    @Test
    public void idsDiferentes_doisAvisosComToquesDiferentes() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 1, "A", "a",
                new Intent(app, ProdutosEmAnaliseActivity.class).putExtra("quem", "um"));
        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 2, "B", "b",
                new Intent(app, ProdutosEmAnaliseActivity.class).putExtra("quem", "dois"));

        assertEquals(2, publicadas().size());
        String primeiro = shadowOf(publicadas().get(0).contentIntent).getSavedIntents()[1].getStringExtra("quem");
        String segundo = shadowOf(publicadas().get(1).contentIntent).getSavedIntents()[1].getStringExtra("quem");
        assertNotEquals(primeiro, segundo);
    }

    @Test
    public void cancelarTodas_limpaABarra() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 1, "A", "a");
        Notificador.mostrar(app, Notificador.Canal.ANALISE_DE_PRODUTOS, 2, "B", "b");

        Notificador.cancelarTodas(app);

        assertTrue(publicadas().isEmpty());
    }
}
