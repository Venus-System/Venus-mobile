package com.venussystem.venusmobile.repository.api.dto;

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
