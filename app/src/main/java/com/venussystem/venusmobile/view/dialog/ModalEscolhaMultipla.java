package com.venussystem.venusmobile.view.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.venussystem.venusmobile.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal de varias escolhas, para editar no perfil as perguntas que aceitam mais
 * de uma resposta (condicoes de pele, gestacao). Mesmo visual e mesmo layout do
 * ModalEscolhaUnica, com o grupo de chips liberado para marcar varios.
 *
 * As opcoes exclusivas ("nenhuma", "prefiro nao dizer") funcionam como nas
 * telas cheias do questionario (PerguntaMultiplaBaseActivity): marcar uma
 * delas desmarca o resto, e marcar qualquer outra desmarca as exclusivas.
 */
public final class ModalEscolhaMultipla {

    public interface AoSalvar {
        void aoConfirmar(List<String> tags);
    }

    private static final String SEPARADOR_ROTULOS = ", ";

    private ModalEscolhaMultipla() {
    }

    public static void mostrar(Context contexto, @StringRes int pergunta, List<String> tagsAtuais,
                               List<ModalEscolhaUnica.Opcao> opcoes, List<String> exclusivas,
                               AoSalvar aoSalvar) {
        View conteudo = LayoutInflater.from(contexto)
                .inflate(R.layout.dialog_escolha_unica, null);

        ((TextView) conteudo.findViewById(R.id.textPergunta)).setText(pergunta);

        String atual = rotulos(contexto, opcoes, tagsAtuais);
        ((TextView) conteudo.findViewById(R.id.textAtual)).setText(contexto.getString(
                R.string.modal_atual,
                atual == null ? contexto.getString(R.string.perfil_pendente) : atual));

        AlertDialog dialog = new AlertDialog.Builder(contexto)
                .setView(conteudo)
                .create();

        ChipGroup grupo = conteudo.findViewById(R.id.grupoOpcoes);
        grupo.setSingleSelection(false);
        grupo.setSelectionRequired(false);

        Map<ModalEscolhaUnica.Opcao, Chip> chips = new LinkedHashMap<>();
        for (ModalEscolhaUnica.Opcao opcao : opcoes) {
            Chip chip = ModalEscolhaUnica.criarChip(contexto, opcao);
            chip.setChecked(tagsAtuais.contains(opcao.tag));
            grupo.addView(chip);
            chips.put(opcao, chip);
        }

        // Os ouvintes entram depois de marcar o que ja estava salvo, para a
        // regra das exclusivas nao mexer na resposta so por abrir o modal.
        for (Map.Entry<ModalEscolhaUnica.Opcao, Chip> entrada : chips.entrySet()) {
            ModalEscolhaUnica.Opcao opcao = entrada.getKey();
            entrada.getValue().setOnCheckedChangeListener((botao, marcado) -> {
                if (marcado) {
                    desmarcarIncompativeis(chips, opcao, exclusivas);
                }
            });
        }

        conteudo.findViewById(R.id.btnCancelarModal).setOnClickListener(v -> dialog.dismiss());
        conteudo.findViewById(R.id.btnSalvarModal).setOnClickListener(v -> {
            List<String> escolhidas = new ArrayList<>();
            for (Map.Entry<ModalEscolhaUnica.Opcao, Chip> entrada : chips.entrySet()) {
                if (entrada.getValue().isChecked()) {
                    escolhidas.add(entrada.getKey().tag);
                }
            }
            // Lista vazia quer dizer "nao respondeu" (ver PerfilRepository):
            // quem nao tem nenhuma marca a opcao propria para isso.
            if (escolhidas.isEmpty()) {
                Toast.makeText(contexto, R.string.pergunta_escolha_ao_menos_uma,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            aoSalvar.aoConfirmar(escolhidas);
            dialog.dismiss();
        });

        ModalEscolhaUnica.prepararJanela(dialog, contexto);
        dialog.show();
    }

    /**
     * O texto do cartao do perfil: os rotulos das tags salvas, na ordem das
     * opcoes. Null quando nao ha nenhuma, que o perfil mostra como pendente.
     */
    @Nullable
    public static String rotulos(Context contexto, List<ModalEscolhaUnica.Opcao> opcoes,
                                 List<String> tags) {
        List<String> rotulos = new ArrayList<>();
        for (ModalEscolhaUnica.Opcao opcao : opcoes) {
            if (tags.contains(opcao.tag)) {
                rotulos.add(contexto.getString(opcao.rotulo));
            }
        }
        return rotulos.isEmpty() ? null : String.join(SEPARADOR_ROTULOS, rotulos);
    }

    private static void desmarcarIncompativeis(Map<ModalEscolhaUnica.Opcao, Chip> chips,
                                               ModalEscolhaUnica.Opcao marcada,
                                               List<String> exclusivas) {
        boolean marcouExclusiva = exclusivas.contains(marcada.tag);
        for (Map.Entry<ModalEscolhaUnica.Opcao, Chip> entrada : chips.entrySet()) {
            ModalEscolhaUnica.Opcao outra = entrada.getKey();
            if (outra != marcada && (marcouExclusiva || exclusivas.contains(outra.tag))) {
                entrada.getValue().setChecked(false);
            }
        }
    }
}
