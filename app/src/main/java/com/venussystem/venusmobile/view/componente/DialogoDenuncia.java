package com.venussystem.venusmobile.view.componente;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.avaliacao.RegrasDaAvaliacao;
import com.venussystem.venusmobile.model.Avaliacao;
import com.venussystem.venusmobile.repository.AvaliacoesRepository;
import com.venussystem.venusmobile.view.util.MensagemRapida;

public final class DialogoDenuncia {

    private DialogoDenuncia() {
    }

    public static void mostrar(Activity tela, Avaliacao avaliacao,
                               AvaliacoesRepository repositorio, View ancora) {
        View conteudo = tela.getLayoutInflater()
                .inflate(R.layout.dialog_denunciar_avaliacao, null, false);
        RadioGroup motivos = conteudo.findViewById(R.id.grupoMotivos);
        EditText detalhes = conteudo.findViewById(R.id.editDetalhesDenuncia);
        TextView erro = conteudo.findViewById(R.id.textErroDenuncia);

        AlertDialog dialogo = new AlertDialog.Builder(tela)
                .setTitle(R.string.denuncia_titulo)
                .setView(conteudo)
                .setPositiveButton(R.string.denuncia_enviar, null)
                .setNegativeButton(R.string.escrever_cancelar, null)
                .create();

        dialogo.setOnShowListener(d -> {
            Button enviar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            motivos.setOnCheckedChangeListener((grupo, id) -> erro.setVisibility(View.GONE));
            enviar.setOnClickListener(v -> {
                int escolhido = motivos.getCheckedRadioButtonId();
                if (escolhido == View.NO_ID) {
                    mostrarErro(erro, R.string.denuncia_erro_motivo);
                    return;
                }
                String rotulo = ((RadioButton) conteudo.findViewById(escolhido))
                        .getText().toString();
                String motivo = RegrasDaAvaliacao.motivoDaDenuncia(
                        rotulo, detalhes.getText().toString());

                enviar.setEnabled(false);
                enviar.setText(R.string.denuncia_enviando);
                erro.setVisibility(View.GONE);
                dialogo.setCancelable(false);
                repositorio.denunciar(avaliacao.getId(), motivo, resultado -> {
                    if (tela.isFinishing() || tela.isDestroyed()) {
                        return;
                    }
                    if (resultado == AvaliacoesRepository.Envio.OK) {
                        dialogo.dismiss();
                        MensagemRapida.sucesso(ancora, tela.getString(R.string.denuncia_enviada))
                                .show();
                        return;
                    }
                    enviar.setEnabled(true);
                    enviar.setText(R.string.denuncia_enviar);
                    dialogo.setCancelable(true);
                    mostrarErro(erro, resultado == AvaliacoesRepository.Envio.SEM_CONTA
                            ? R.string.avaliacao_sem_conta
                            : R.string.denuncia_erro_envio);
                });
            });
        });
        dialogo.show();
    }

    private static void mostrarErro(TextView erro, int texto) {
        erro.setText(texto);
        erro.setVisibility(View.VISIBLE);
    }
}
