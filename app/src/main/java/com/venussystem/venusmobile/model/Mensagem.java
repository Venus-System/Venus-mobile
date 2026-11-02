package com.venussystem.venusmobile.model;

public class Mensagem {

    public static final int DO_ASSISTENTE = 0;
    public static final int DO_USUARIO = 1;

    private final int autor;
    private final String texto;
    private final boolean digitando;

    private Mensagem(int autor, String texto, boolean digitando) {
        this.autor = autor;
        this.texto = texto;
        this.digitando = digitando;
    }

    public static Mensagem doAssistente(String texto) {
        return new Mensagem(DO_ASSISTENTE, texto, false);
    }

    public static Mensagem doUsuario(String texto) {
        return new Mensagem(DO_USUARIO, texto, false);
    }

    public static Mensagem digitando(String texto) {
        return new Mensagem(DO_ASSISTENTE, texto, true);
    }

    public int getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }

    public boolean isDigitando() {
        return digitando;
    }
}
