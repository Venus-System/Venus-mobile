package com.venussystem.venusmobile.model;

public class Mensagem {

    public static final int DO_ASSISTENTE = 0;
    public static final int DO_USUARIO = 1;

    private final int autor;
    private final String texto;

    private Mensagem(int autor, String texto) {
        this.autor = autor;
        this.texto = texto;
    }

    public static Mensagem doAssistente(String texto) {
        return new Mensagem(DO_ASSISTENTE, texto);
    }

    public static Mensagem doUsuario(String texto) {
        return new Mensagem(DO_USUARIO, texto);
    }

    public int getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }
}
