package com.venussystem.venusmobile.domain.perfil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Quanto do perfil da pessoa ja foi respondido, para o cartao "Seu perfil
 * Venus": a mesma conta do site (respondidos / campos contados, arredondado).
 * Java puro, sem Android: se testa sem emulador.
 *
 * Os campos contados sao os 9 do site: faixa etaria, genero, tipo de pele,
 * fototipo, sensibilidade, tipo de cabelo, couro cabeludo, situacao atual
 * (gestacao e amamentacao) e condicoes de pele. No site, os de saude so entram
 * na conta com o consentimento de dados de saude; o app nao tem esse passo, entao
 * conta todos.
 */
public final class ProgressoPerfil {

    public enum Estado {
        /** Nunca respondeu nada: o cartao chama para o questionario. */
        SEM_QUESTIONARIO,
        /** Respondeu parte: mostra a barra e o que falta. */
        INCOMPLETO,
        /** Respondeu tudo: o cartao some. */
        COMPLETO
    }

    public final Estado estado;
    /** 0 a 100. */
    public final int porcentagem;
    /** Os campos ainda sem resposta, na ordem em que foram contados. */
    public final List<String> faltando;

    private ProgressoPerfil(Estado estado, int porcentagem, List<String> faltando) {
        this.estado = estado;
        this.porcentagem = porcentagem;
        this.faltando = Collections.unmodifiableList(faltando);
    }

    /**
     * @param questionarioRespondido a pessoa chegou ao fim do questionario
     * @param campos                 os campos que contam
     * @param respondido             diz se um campo tem resposta
     */
    public static ProgressoPerfil calcular(boolean questionarioRespondido, List<String> campos,
                                           Predicate<String> respondido) {
        List<String> faltando = new ArrayList<>();
        for (String campo : campos) {
            if (!respondido.test(campo)) {
                faltando.add(campo);
            }
        }
        int total = campos.size();
        int preenchidos = total - faltando.size();
        int porcentagem = total == 0 ? 100 : (int) Math.round(100.0 * preenchidos / total);

        Estado estado;
        if (faltando.isEmpty()) {
            estado = Estado.COMPLETO;
        } else if (preenchidos == 0 && !questionarioRespondido) {
            estado = Estado.SEM_QUESTIONARIO;
        } else {
            estado = Estado.INCOMPLETO;
        }
        return new ProgressoPerfil(estado, porcentagem, faltando);
    }
}
