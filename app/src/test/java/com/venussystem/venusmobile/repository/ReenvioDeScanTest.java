package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.ScanUploadException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadedPhoto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ReenvioDeScanTest {

    private static final String UID = "uid-ana";
    private static final long AGORA = 10_000_000L;
    private static final long PARADO_HA_MUITO = AGORA - ReenvioDeScan.IDADE_MINIMA_MS - 1;

    private EtapasFalsas etapas;

    @Before
    public void setUp() {
        etapas = new EtapasFalsas();
    }

    private ReenvioDeScan.Resultado tentar() throws IOException {
        return new ReenvioDeScan(etapas, () -> false).tentar(UID, AGORA);
    }

    @Test
    public void rascunhoDoZero_fazTodosOsPassosNaOrdem() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", false, false, false);

        ReenvioDeScan.Resultado resultado = tentar();

        assertEquals(Arrays.asList("assinaturas scan-1", "fotos scan-1", "ingredientes scan-1", "cadastrar scan-1"),
                etapas.chamadas);
        assertEquals(Arrays.asList("Hidratante"), resultado.enviados);
        assertFalse(resultado.tentarDepois);
    }

    @Test
    public void fotosJaConfirmadas_naoPedemAssinaturaNemSobemDeNovo() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, true, false);

        tentar();

        assertEquals(Arrays.asList("ingredientes scan-1", "cadastrar scan-1"), etapas.chamadas);
    }

    @Test
    public void soUmaFotoConfirmada_sobeAsFotosDeNovo() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, false, false);

        tentar();

        assertEquals(Arrays.asList("assinaturas scan-1", "fotos scan-1", "ingredientes scan-1", "cadastrar scan-1"),
                etapas.chamadas);
    }

    @Test
    public void ingredientesJaConsultados_vaiDireitoParaOCadastro() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, true, true);

        tentar();

        assertEquals(Arrays.asList("cadastrar scan-1"), etapas.chamadas);
    }

    @Test
    public void semRascunhosPendentes_naoFazNada() throws Exception {
        ReenvioDeScan.Resultado resultado = tentar();

        assertTrue(etapas.chamadas.isEmpty());
        assertTrue(resultado.enviados.isEmpty());
        assertFalse(resultado.tentarDepois);
    }

    @Test
    public void varioRascunhos_enviaCadaUm() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, true, true);
        etapas.adicionar("scan-2", "Tônico", true, true, true);

        ReenvioDeScan.Resultado resultado = tentar();

        assertEquals(Arrays.asList("Hidratante", "Tônico"), resultado.enviados);
    }

    @Test
    public void rascunhoAlteradoHaPouco_podeEstarSendoEnviadoPelaTela_ficaParaDepois() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", false, false, false);
        etapas.ultimaAlteracao.put("scan-1", AGORA - 30_000L);

        ReenvioDeScan.Resultado resultado = tentar();

        assertTrue(etapas.chamadas.isEmpty());
        assertTrue(resultado.tentarDepois);
    }

    @Test
    public void umRascunhoNovoNaoImpedeOsOutros() throws Exception {
        etapas.adicionar("scan-1", "Novo", true, true, true);
        etapas.ultimaAlteracao.put("scan-1", AGORA - 10_000L);
        etapas.adicionar("scan-2", "Velho", true, true, true);

        ReenvioDeScan.Resultado resultado = tentar();

        assertEquals(Arrays.asList("Velho"), resultado.enviados);
        assertTrue(resultado.tentarDepois);
    }

    @Test
    public void trabalhoCancelado_paraEFicaParaDepois() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, true, true);

        ReenvioDeScan.Resultado resultado = new ReenvioDeScan(etapas, () -> true).tentar(UID, AGORA);

        assertTrue(etapas.chamadas.isEmpty());
        assertTrue(resultado.tentarDepois);
    }

    @Test
    public void semRede_naoEnviaEPedeParaTentarDepois() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", false, false, false);
        etapas.falhaEm("assinaturas", new ScanApiException(ScanApiException.Kind.NETWORK, 0, "sem rede"));

        ReenvioDeScan.Resultado resultado = tentar();

        assertTrue(resultado.enviados.isEmpty());
        assertTrue(resultado.tentarDepois);
    }

    @Test
    public void umaFalhaNaoImpedeOsDemaisRascunhos() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", true, true, true);
        etapas.adicionar("scan-2", "Tônico", true, true, true);
        etapas.falhaEm("cadastrar scan-1", new ScanApiException(ScanApiException.Kind.TIMEOUT, 0, "lento"));

        ReenvioDeScan.Resultado resultado = tentar();

        assertEquals(Arrays.asList("Tônico"), resultado.enviados);
        assertTrue(resultado.tentarDepois);
    }

    @Test
    public void contaSemPermissao_naoInsisteNemConectaConta() throws Exception {
        etapas.adicionar("scan-1", "Hidratante", false, false, false);
        etapas.falhaEm("assinaturas", new ScanApiException(ScanApiException.Kind.FORBIDDEN, 403, "sem cadastro"));

        ReenvioDeScan.Resultado resultado = tentar();

        assertFalse(resultado.tentarDepois);
        assertEquals(Arrays.asList("assinaturas scan-1"), etapas.chamadas);
    }

    @Test
    public void transitorio_redeEServidorSim_authEDadoNao() {
        assertTrue(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.NETWORK, 0, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.TIMEOUT, 0, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.HTTP, 503, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.HTTP, 429, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.HTTP, 408, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.HTTP, 400, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.AUTH, 401, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.FORBIDDEN, 403, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.INVALID_REQUEST, 422, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanApiException(ScanApiException.Kind.INVALID_RESPONSE, 200, "")));
    }

    @Test
    public void transitorio_uploadComAssinaturaVencidaOuRedeSim_configuracaoNao() {
        assertTrue(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.NETWORK, 0, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.EXPIRED_SIGNATURE, 401, "")));
        assertTrue(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.HTTP, 500, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.HTTP, 403, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.CONFIGURATION, 0, "")));
        assertFalse(ReenvioDeScan.transitorio(new ScanUploadException(ScanUploadException.Kind.LOCAL, 0, "")));
    }

    @Test
    public void transitorio_tempoEsgotadoEIoSim_estadoInvalidoNao() {
        assertTrue(ReenvioDeScan.transitorio(new TimeoutException("demorou")));
        assertTrue(ReenvioDeScan.transitorio(new IOException("rede")));
        assertFalse(ReenvioDeScan.transitorio(new IllegalStateException("fotos nao confirmadas")));
    }

    private static final class EtapasFalsas implements ReenvioDeScan.Etapas {
        final List<ScanDraftStore.SavedDraft> rascunhos = new ArrayList<>();
        final Map<String, Long> ultimaAlteracao = new HashMap<>();
        final List<String> chamadas = new ArrayList<>();
        private final Map<String, Exception> falhas = new HashMap<>();

        void adicionar(String scanId, String nome, boolean frente, boolean verso, boolean ingredientes) {
            ScanSubmissionDraft draft = new Gson().fromJson(
                    "{\"scanId\":\"" + scanId + "\",\"productCandidates\":[\"" + nome + "\"]}",
                    ScanSubmissionDraft.class);
            ScanDraftStore.SavedDraft salvo = new ScanDraftStore.SavedDraft();
            salvo.firebaseUid = UID;
            salvo.draft = draft;
            if (frente) salvo.frontUpload = new ScanUploadedPhoto();
            if (verso) salvo.backUpload = new ScanUploadedPhoto();
            if (ingredientes) salvo.ingredientCatalog = new ScanIngredientCatalog.Report();
            rascunhos.add(salvo);
            ultimaAlteracao.put(scanId, PARADO_HA_MUITO);
        }

        void falhaEm(String chave, Exception erro) {
            falhas.put(chave, erro);
        }

        private void passo(String nome, String scanId) throws Exception {
            chamadas.add(nome + " " + scanId);
            Exception erro = falhas.containsKey(nome + " " + scanId) ? falhas.get(nome + " " + scanId) : falhas.get(nome);
            if (erro != null) {
                throw erro;
            }
        }

        @Override
        public List<ScanDraftStore.SavedDraft> pendentes(String uid) {
            return rascunhos;
        }

        @Override
        public long ultimaAlteracaoMs(String uid, String scanId) {
            return ultimaAlteracao.get(scanId);
        }

        @Override
        public ScanUploadSignaturesResponse assinaturas(String uid, String scanId) throws Exception {
            passo("assinaturas", scanId);
            return new ScanUploadSignaturesResponse();
        }

        @Override
        public void enviarFotos(String uid, String scanId, ScanUploadSignaturesResponse assinaturas) throws Exception {
            passo("fotos", scanId);
        }

        @Override
        public void consultarIngredientes(String uid, String scanId) throws Exception {
            passo("ingredientes", scanId);
        }

        @Override
        public void cadastrar(String uid, String scanId) throws Exception {
            passo("cadastrar", scanId);
        }
    }
}
