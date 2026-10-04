package com.venussystem.venusmobile.repository.api.dto;

/**
 * Um item de GET /api/scan-sessions/user/{userId}, so com o que a tela de
 * produtos em analise mostra. O Gson ignora o resto (ingredientes, OCR do
 * verso, aparelho...).
 *
 * Nao e o ScanSessionResponse do fluxo de scan de proposito: aquele e gravado
 * no aparelho como recibo do envio (ScanDraftStore), e o fluxo apaga o OCR do
 * aparelho depois de enviar. Com nome e marca nele, o rotulo voltaria a ficar
 * guardado.
 */
public class ScanEnviadoResponse {
    public String id;
    public String status;
    public String createdAt;
    public Imagens images;
    public Ocr ocr;
    public Decisao review;
    public Sincronizacao sync;

    public static class Imagens {
        public Imagem front;
        public Imagem back;
    }

    public static class Imagem {
        public String secureUrl;
    }

    public static class Ocr {
        public Frente front;
    }

    public static class Frente {
        public Extraido extracted;
    }

    public static class Extraido {
        public String productName;
        public String brand;
    }

    public static class Decisao {
        public String decidedAt;
        public String reason;
    }

    public static class Sincronizacao {
        public Long productId;
    }
}
