package com.venussystem.venusmobile.domain.perfil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class ProgressoPerfil {

    public enum Estado {
        SEM_QUESTIONARIO,
        INCOMPLETO,
        COMPLETO
    }

    public final Estado estado;
    public final int porcentagem;
    public final List<String> faltando;

    private ProgressoPerfil(Estado estado, int porcentagem, List<String> faltando) {
        this.estado = estado;
        this.porcentagem = porcentagem;
        this.faltando = Collections.unmodifiableList(faltando);
    }

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
