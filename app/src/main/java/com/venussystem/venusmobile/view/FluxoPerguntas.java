package com.venussystem.venusmobile.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FluxoPerguntas {

    private static final String EXTRA_RESTANTES = "venus_perguntas_restantes";

    private static final Map<String, Class<?>> TELAS = new LinkedHashMap<>();

    static {
        TELAS.put(PerfilRepository.GENERO, PerguntaGeneroActivity.class);
        TELAS.put(PerfilRepository.TIPO_CABELO, PerguntaCabeloActivity.class);
        TELAS.put(PerfilRepository.COURO_CABELUDO, PerguntaCouroCabeludoActivity.class);
        TELAS.put(PerfilRepository.TIPO_PELE, PerguntaTipoPeleActivity.class);
        TELAS.put(PerfilRepository.SENSIBILIDADE, PerguntaSensibilidadeActivity.class);
        TELAS.put(PerfilRepository.CONDICOES_PELE, PerguntaCondicoesPeleActivity.class);
        TELAS.put(PerfilRepository.FOTOTIPO, PerguntaFototipoActivity.class);
        TELAS.put(PerfilRepository.FAIXA_ETARIA, PerguntaFaixaEtariaActivity.class);
        TELAS.put(PerfilRepository.GESTACAO, PerguntaGestacaoActivity.class);
    }

    private FluxoPerguntas() {
    }

    public static boolean iniciar(Context contexto, List<String> faltando) {
        List<String> restantes = ordenar(faltando);
        if (restantes.isEmpty()) {
            return false;
        }
        Intent intent = intentDaPrimeira(contexto, restantes);
        if (!(contexto instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        contexto.startActivity(intent);
        return true;
    }

    public static boolean avulso(Intent intent) {
        return intent.hasExtra(EXTRA_RESTANTES);
    }

    public static void seguir(Activity atual, String chaveRespondida) {
        PerfilRepository perfil = new PerfilRepository(atual);
        ArrayList<String> restantes = restantesDe(atual.getIntent());
        restantes.remove(chaveRespondida);
        if (!perfil.perguntaGestacao()) {
            restantes.remove(PerfilRepository.GESTACAO);
        }

        if (restantes.isEmpty()) {
            perfil.marcarQuestionarioRespondido();
            atual.finish();
            return;
        }

        atual.startActivity(intentDaPrimeira(atual, restantes));
        atual.finish();
    }

    private static Intent intentDaPrimeira(Context contexto, List<String> restantes) {
        return new Intent(contexto, TELAS.get(restantes.get(0)))
                .putStringArrayListExtra(EXTRA_RESTANTES, new ArrayList<>(restantes));
    }

    private static ArrayList<String> restantesDe(Intent intent) {
        ArrayList<String> restantes = intent.getStringArrayListExtra(EXTRA_RESTANTES);
        return restantes == null ? new ArrayList<>() : restantes;
    }

    private static List<String> ordenar(List<String> faltando) {
        List<String> ordenadas = new ArrayList<>();
        for (String chave : TELAS.keySet()) {
            if (faltando.contains(chave)) {
                ordenadas.add(chave);
            }
        }
        return ordenadas;
    }
}
