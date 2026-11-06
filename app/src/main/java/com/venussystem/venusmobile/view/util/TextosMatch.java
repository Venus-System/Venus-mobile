package com.venussystem.venusmobile.view.util;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.match.EtiquetasDoPerfil;
import com.venussystem.venusmobile.domain.match.RespostasDaNota;
import com.venussystem.venusmobile.domain.match.ResultadoMatch;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TextosMatch {
    public interface Recursos {
        String texto(@StringRes int id, Object... argumentos);
    }

    private static final Locale PT_BR = new Locale("pt", "BR");

    private static final Map<String, Integer> TITULO_ALERTA = new HashMap<>();
    private static final Map<String, Integer> TITULO_GRAVE = new HashMap<>();
    private static final Map<String, Integer> TITULO_BENEFICIO = new HashMap<>();
    private static final Map<String, Integer> TITULO_AUSENCIA = new HashMap<>();
    private static final Map<String, Integer> LINHA_EVITAR = new HashMap<>();
    private static final Map<String, Integer> CHIP = new HashMap<>();
    private static final Map<String, Integer> ERRO_DA_NOTA = new HashMap<>();
    private static final Map<String, Integer> RESPOSTA = new HashMap<>();

    static {
        RESPOSTA.put("controle-de-oleosidade", R.string.match_resposta_pele_oleosa);
        RESPOSTA.put("muito-seca", R.string.match_resposta_pele_seca);
        RESPOSTA.put("hidratacao", R.string.match_resposta_pele_seca);
        RESPOSTA.put("reativa", R.string.match_resposta_pele_sensivel);
        RESPOSTA.put("pele-acneica", R.string.match_resposta_acne);
        RESPOSTA.put("rosacea", R.string.match_resposta_rosacea);
        RESPOSTA.put("eczema", R.string.match_resposta_eczema);
        RESPOSTA.put("hiperpigmentacao", R.string.match_resposta_hiperpigmentacao);
        RESPOSTA.put("melasma", R.string.match_resposta_melasma);
        RESPOSTA.put("caspa", R.string.match_resposta_caspa);
        RESPOSTA.put("couro-cabeludo-seco", R.string.match_resposta_couro_seco);
        RESPOSTA.put("couro-cabeludo-oleoso", R.string.match_resposta_couro_oleoso);
        RESPOSTA.put("couro-cabeludo-sensivel", R.string.match_resposta_couro_sensivel);
        RESPOSTA.put("gravidez", R.string.match_resposta_gravidez);
        RESPOSTA.put("amamentacao", R.string.match_resposta_amamentacao);
        RESPOSTA.put("sem-parabenos", R.string.match_resposta_sem_parabenos);
        RESPOSTA.put("sem-sulfato", R.string.match_resposta_sem_sulfatos);
        RESPOSTA.put("sem-silicone", R.string.match_resposta_sem_silicones);
        RESPOSTA.put("sem-fragrancia", R.string.match_resposta_sem_fragrancia);

        ERRO_DA_NOTA.put("PROFILE_NOT_FOUND", R.string.produto_match_erro_perfil);
        ERRO_DA_NOTA.put("PRODUCT_NOT_FOUND", R.string.produto_match_erro_produto);
        ERRO_DA_NOTA.put("VERSION_NOT_FOUND", R.string.produto_match_erro_produto);
        ERRO_DA_NOTA.put("VERSION_UNDER_REVIEW", R.string.produto_match_erro_em_revisao);
        ERRO_DA_NOTA.put("NO_INGREDIENTS", R.string.produto_match_erro_sem_ingredientes);
        ERRO_DA_NOTA.put("SCORING_MODEL_NOT_FOUND", R.string.produto_match_erro_calculo);
        ERRO_DA_NOTA.put("NO_ACTIVE_SCORING_MODEL", R.string.produto_match_erro_calculo);

        TITULO_ALERTA.put("sem-silicone", R.string.match_titulo_contem_silicone);
        TITULO_ALERTA.put("sem-sulfato", R.string.match_titulo_contem_sulfato);
        TITULO_ALERTA.put("sem-parabenos", R.string.match_titulo_contem_parabeno);
        TITULO_ALERTA.put("gravidez", R.string.match_titulo_cautela_gestacao);
        TITULO_ALERTA.put("amamentacao", R.string.match_titulo_cautela_amamentacao);
        TITULO_ALERTA.put("reativa", R.string.match_titulo_atencao_reativa);

        TITULO_GRAVE.put("gravidez", R.string.match_titulo_contraindicado_gestacao);

        TITULO_BENEFICIO.put("hidratacao", R.string.match_titulo_ajuda_pele_seca);
        TITULO_BENEFICIO.put("controle-de-frizz", R.string.match_titulo_ajuda_frizz);
        TITULO_BENEFICIO.put("caspa", R.string.match_titulo_ajuda_caspa);

        TITULO_AUSENCIA.put("sem-silicone", R.string.match_titulo_sem_silicones);
        TITULO_AUSENCIA.put("sem-sulfato", R.string.match_titulo_sem_sulfatos);
        TITULO_AUSENCIA.put("sem-parabenos", R.string.match_titulo_sem_parabenos);
        TITULO_AUSENCIA.put("gravidez", R.string.match_titulo_sem_contraindicado_gestacao);
        TITULO_AUSENCIA.put("amamentacao", R.string.match_titulo_sem_cautela_amamentacao);
        TITULO_AUSENCIA.put("reativa", R.string.match_titulo_sem_atencao_reativa);

        LINHA_EVITAR.put("sem-silicone", R.string.match_linha_evitar_silicones);
        LINHA_EVITAR.put("sem-sulfato", R.string.match_linha_evitar_sulfatos);
        LINHA_EVITAR.put("sem-parabenos", R.string.match_linha_evitar_parabenos);

        CHIP.put("hidratacao", R.string.match_chip_hidratacao);
        CHIP.put("reparo-da-barreira", R.string.match_chip_barreira);
        CHIP.put("usuario-outdoor", R.string.match_chip_protecao_uv);
        CHIP.put("anti-idade", R.string.match_chip_antioxidante);
        CHIP.put("controle-de-frizz", R.string.match_chip_antifrizz);
        CHIP.put("colorido", R.string.match_chip_coloracao);
        CHIP.put("caspa", R.string.match_chip_anticaspa);
        CHIP.put("ingredientes-naturais", R.string.match_chip_origem_vegetal);
        CHIP.put("ingredientes-biotech", R.string.match_chip_aditivo_biologico);
        CHIP.put("rotina-de-academia", R.string.match_chip_suor_odor);
        CHIP.put("clima-quente", R.string.match_chip_suor_odor);
        CHIP.put("sem-silicone", R.string.match_chip_silicone);
        CHIP.put("sem-sulfato", R.string.match_chip_sulfato);
        CHIP.put("sem-parabenos", R.string.match_chip_parabeno);
        CHIP.put("gravidez", R.string.match_chip_gestacao);
        CHIP.put("amamentacao", R.string.match_chip_amamentacao);
        CHIP.put("reativa", R.string.match_chip_pele_reativa);
    }

    private TextosMatch() {
    }

    public static String titulo(Recursos r, ResultadoMatch.Ponto ponto) {
        Integer id = tabelaDeTitulo(ponto.tipo).get(ponto.slug);
        if (id != null) return r.texto(id);
        if (ponto.efeito != null && !ponto.efeito.trim().isEmpty()) return ponto.efeito;
        String rotulo = EtiquetasDoPerfil.rotulos(Collections.singleton(ponto.slug),
                Collections.emptySet()).get(0);
        switch (ponto.tipo) {
            case BENEFICIO:
                return r.texto(R.string.match_titulo_bom_para, rotulo);
            case AUSENCIA:
                return r.texto(R.string.match_titulo_nada_contra, rotulo);
            default:
                return r.texto(R.string.match_titulo_atencao_para, rotulo);
        }
    }

    public static String linha(Recursos r, ResultadoMatch.Ponto ponto) {
        if (ponto.tipo == ResultadoMatch.Tipo.AUSENCIA || ponto.ingredientes.isEmpty()) {
            return "";
        }
        String ingredientes = ingredientes(r, ponto.ingredientes);
        if (ponto.tipo == ResultadoMatch.Tipo.BENEFICIO) {
            return r.texto(R.string.match_linha_beneficio, ingredientes);
        }
        Integer evitar = LINHA_EVITAR.get(ponto.slug);
        return evitar != null
                ? r.texto(evitar, ingredientes)
                : r.texto(R.string.match_linha_encontrado, ingredientes);
    }

    public static String tituloDaResposta(Recursos r, String slug) {
        Integer id = RESPOSTA.get(slug);
        if (id != null) return r.texto(id);
        String rotulo = EtiquetasDoPerfil.rotulos(Collections.singleton(slug),
                Collections.emptySet()).get(0);
        return r.texto(R.string.match_resposta_generica, rotulo);
    }

    public static String linhaDaResposta(Recursos r, RespostasDaNota.Resposta resposta) {
        boolean semIngrediente = resposta.ingredientes.isEmpty();
        String ingredientes = ingredientes(r, resposta.ingredientes);
        switch (resposta.efeito) {
            case BLOQUEOU:
                return semIngrediente
                        ? r.texto(R.string.match_resposta_bloqueou_sem_ingrediente)
                        : r.texto(R.string.match_resposta_bloqueou, ingredientes);
            case TIROU:
                return semIngrediente
                        ? r.texto(R.string.match_resposta_tirou_sem_ingrediente)
                        : r.texto(R.string.match_resposta_tirou, ingredientes);
            case COMBINOU_POUCO:
                return semIngrediente
                        ? r.texto(R.string.match_resposta_pouco_sem_ingrediente, resposta.porcento)
                        : r.texto(R.string.match_resposta_pouco, resposta.porcento, ingredientes);
            default:
                return semIngrediente
                        ? r.texto(R.string.match_resposta_combinou_sem_ingrediente,
                        resposta.porcento)
                        : r.texto(R.string.match_resposta_combinou, resposta.porcento,
                        ingredientes);
        }
    }

    public static List<String> chips(Recursos r, List<ResultadoMatch.Destaque> destaques,
                                     int maximo) {
        Set<String> rotulos = new LinkedHashSet<>();
        for (ResultadoMatch.Destaque d : destaques) {
            if (rotulos.size() >= maximo) break;
            Integer id = d.slug == null ? null : CHIP.get(d.slug);
            String rotulo = id != null ? r.texto(id) : d.efeito;
            if (!rotulo.trim().isEmpty()) rotulos.add(rotulo);
        }
        return new ArrayList<>(rotulos);
    }

    public static String ingredientes(Recursos r, List<String> nomes) {
        if (nomes.isEmpty()) return "";
        String primeiro = formatoTitulo(nomes.get(0));
        if (nomes.size() == 1) return primeiro;
        if (nomes.size() == 2) {
            return r.texto(R.string.match_ingredientes_dois, primeiro, formatoTitulo(nomes.get(1)));
        }
        return r.texto(R.string.match_ingredientes_mais, primeiro, nomes.size() - 1);
    }

    public static String pontos(double valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(PT_BR);
        formato.setMaximumFractionDigits(1);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    @Nullable
    @StringRes
    public static Integer erroDaNota(@Nullable String codigo) {
        return codigo == null ? null : ERRO_DA_NOTA.get(codigo);
    }

    static String formatoTitulo(String nome) {
        if (nome == null || !nome.equals(nome.toUpperCase(Locale.ROOT))) {
            return nome == null ? "" : nome;
        }
        StringBuilder saida = new StringBuilder(nome.length());
        boolean inicio = true;
        for (char c : nome.toLowerCase(Locale.ROOT).toCharArray()) {
            saida.append(inicio ? Character.toUpperCase(c) : c);
            inicio = !Character.isLetterOrDigit(c);
        }
        return saida.toString();
    }

    private static Map<String, Integer> tabelaDeTitulo(ResultadoMatch.Tipo tipo) {
        switch (tipo) {
            case GRAVE:
                return TITULO_GRAVE;
            case ALERTA:
                return TITULO_ALERTA;
            case BENEFICIO:
                return TITULO_BENEFICIO;
            default:
                return TITULO_AUSENCIA;
        }
    }
}
