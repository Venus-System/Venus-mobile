package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.CatalogoAlergiasRepository;
import com.venussystem.venusmobile.repository.FotoPerfilRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.SincronizacaoRepository;
import com.venussystem.venusmobile.view.BemVindoActivity;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;
import com.venussystem.venusmobile.view.componente.AvatarPerfilView;
import com.venussystem.venusmobile.view.dialog.MenuLateralPerfil;
import com.venussystem.venusmobile.view.dialog.ModalEscolhaMultipla;
import com.venussystem.venusmobile.view.dialog.ModalEscolhaUnica;
import com.venussystem.venusmobile.view.dialog.ModalFaixaEtaria;
import com.venussystem.venusmobile.view.util.ImagemLocalUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PerfilFragment extends Fragment {

    /**
     * Um cartao da secao "Pele & cabelo": o titulo, de onde le/salva a
     * resposta, a pergunta do modal e as opcoes de chip. `opcoes == null`
     * marca a faixa etaria, que usa um modal de dropdown em vez de chips.
     * `exclusivas != null` marca as perguntas de varias escolhas, salvas como
     * lista (condicoes de pele e gestacao).
     */
    private static class Atributo {
        @StringRes final int rotulo;
        final String chave;
        @StringRes final int pergunta;
        @Nullable final List<ModalEscolhaUnica.Opcao> opcoes;
        @Nullable final List<String> exclusivas;

        Atributo(@StringRes int rotulo, String chave, @StringRes int pergunta,
                 @Nullable List<ModalEscolhaUnica.Opcao> opcoes) {
            this(rotulo, chave, pergunta, opcoes, null);
        }

        Atributo(@StringRes int rotulo, String chave, @StringRes int pergunta,
                 @Nullable List<ModalEscolhaUnica.Opcao> opcoes,
                 @Nullable List<String> exclusivas) {
            this.rotulo = rotulo;
            this.chave = chave;
            this.pergunta = pergunta;
            this.opcoes = opcoes;
            this.exclusivas = exclusivas;
        }
    }

    private static List<ModalEscolhaUnica.Opcao> opcoes(Object... paresTagRotulo) {
        List<ModalEscolhaUnica.Opcao> lista = new ArrayList<>();
        for (int i = 0; i < paresTagRotulo.length; i += 2) {
            lista.add(new ModalEscolhaUnica.Opcao(
                    (String) paresTagRotulo[i], (Integer) paresTagRotulo[i + 1]));
        }
        return lista;
    }

    // As mesmas tags/opcoes das telas cheias do questionario (para a resposta
    // continuar compativel), na ordem em que os cartoes aparecem na tela.
    private static final Atributo[] ATRIBUTOS = {
            new Atributo(R.string.perfil_genero, PerfilRepository.GENERO,
                    R.string.pergunta_genero, opcoes(
                    "FEMALE", R.string.genero_feminino,
                    "MALE", R.string.genero_masculino,
                    "NON_BINARY", R.string.genero_nao_binario,
                    "OTHER", R.string.genero_outro,
                    "PREFER_NOT_SAY", R.string.genero_nao_identificar)),

            new Atributo(R.string.perfil_tipo_pele, PerfilRepository.TIPO_PELE,
                    R.string.pergunta_tipo_pele, opcoes(
                    "OILY", R.string.pele_oleoso,
                    "COMBINATION", R.string.pele_misto,
                    "DRY", R.string.pele_seca,
                    "NORMAL", R.string.pele_normal,
                    "SENSITIVE", R.string.pele_sensivel)),

            new Atributo(R.string.perfil_fototipo, PerfilRepository.FOTOTIPO,
                    R.string.pergunta_fototipo, opcoes(
                    "I", R.string.foto_i,
                    "II", R.string.foto_ii,
                    "III", R.string.foto_iii,
                    "IV", R.string.foto_iv,
                    "V", R.string.foto_v,
                    "VI", R.string.foto_vi)),

            new Atributo(R.string.perfil_sensibilidade, PerfilRepository.SENSIBILIDADE,
                    R.string.pergunta_sensibilidade, opcoes(
                    "LOW", R.string.sens_baixa,
                    "MEDIUM", R.string.sens_media,
                    "HIGH", R.string.sens_alta)),

            new Atributo(R.string.perfil_condicoes_pele, PerfilRepository.CONDICOES_PELE,
                    R.string.pergunta_condicoes_pele, opcoes(
                    "acneProne", R.string.condicao_acne,
                    "hasRosacea", R.string.condicao_rosacea,
                    "hasEczema", R.string.condicao_eczema,
                    "hasHyperpigmentation", R.string.condicao_hiperpigmentacao,
                    "hasMelasma", R.string.condicao_melasma,
                    PerfilRepository.NENHUMA, R.string.condicao_nenhuma),
                    Collections.singletonList(PerfilRepository.NENHUMA)),

            new Atributo(R.string.perfil_tipo_cabelo, PerfilRepository.TIPO_CABELO,
                    R.string.pergunta_tipo_cabelo, opcoes(
                    "TYPE_1", R.string.cabelo_1,
                    "TYPE_2A", R.string.cabelo_2a,
                    "TYPE_2B", R.string.cabelo_2b,
                    "TYPE_2C", R.string.cabelo_2c,
                    "TYPE_3A", R.string.cabelo_3a,
                    "TYPE_3B", R.string.cabelo_3b,
                    "TYPE_3C", R.string.cabelo_3c,
                    "TYPE_4A", R.string.cabelo_4a,
                    "TYPE_4B", R.string.cabelo_4b,
                    "TYPE_4C", R.string.cabelo_4c)),

            new Atributo(R.string.perfil_couro, PerfilRepository.COURO_CABELUDO,
                    R.string.pergunta_couro, opcoes(
                    "NORMAL", R.string.couro_normal,
                    "DRY", R.string.couro_seco,
                    "SENSITIVE", R.string.couro_sensivel,
                    "OILY", R.string.couro_oleoso,
                    "DANDRUFF", R.string.couro_caspa,
                    "OTHER", R.string.couro_outro)),

            new Atributo(R.string.perfil_faixa_etaria, PerfilRepository.FAIXA_ETARIA,
                    R.string.pergunta_faixa_etaria, null),

            // Muda com o tempo (a gestacao acaba, a amamentacao tambem), entao
            // precisa ser editavel aqui e nao so no questionario inicial.
            new Atributo(R.string.perfil_gestacao, PerfilRepository.GESTACAO,
                    R.string.pergunta_gestacao, opcoes(
                    "isPregnant", R.string.gestacao_gravida,
                    "isBreastfeeding", R.string.gestacao_amamentando,
                    PerfilRepository.NENHUMA, R.string.gestacao_nenhuma,
                    PerfilRepository.PREFIRO_NAO_DIZER, R.string.gestacao_nao_dizer),
                    Arrays.asList(PerfilRepository.NENHUMA, PerfilRepository.PREFIRO_NAO_DIZER)),
    };

    private static final String ESTADO_URI_CAMERA = "uri_camera";

    private PerfilRepository perfil;
    private FotoPerfilRepository fotoPerfil;
    private LinearLayout listaAtributos;

    // O que o cabecalho mostra; o menu lateral repete o mesmo nome e e-mail.
    private String nomeExibido = "";
    private String emailExibido = "";

    // Onde a camera vai gravar a foto. Guardado no estado da tela porque o
    // sistema pode fechar o app enquanto a camera esta aberta.
    @Nullable
    private Uri uriCameraPendente;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickerGaleria =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    trocarFoto(uri);
                }
            });

    private final ActivityResultLauncher<Uri> pickerCamera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), sucesso -> {
                if (Boolean.TRUE.equals(sucesso) && uriCameraPendente != null) {
                    trocarFoto(uriCameraPendente);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            uriCameraPendente = savedInstanceState.getParcelable(ESTADO_URI_CAMERA, Uri.class);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ESTADO_URI_CAMERA, uriCameraPendente);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_perfil, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        perfil = new PerfilRepository(requireContext());
        listaAtributos = view.findViewById(R.id.listaAtributos);

        mostrarUsuario(view);
        montarAtributos();

        view.findViewById(R.id.btnMenuPerfil).setOnClickListener(v -> abrirMenuLateral());

        // A foto guardada aparece na hora; sem foto no aparelho, a API e
        // consultada (foto posta pelo site ou em outro celular).
        fotoPerfil = new FotoPerfilRepository(requireContext());
        mostrarFoto(fotoPerfil.caminho());
        fotoPerfil.buscarDaApiEmSegundoPlano(this::mostrarFoto);

        CatalogoAlergiasRepository catalogo = new CatalogoAlergiasRepository(requireContext());
        prepararSecaoDeLista(view, R.id.campoAlergia, R.id.chipsAlergias,
                catalogo.nomes(), PerfilRepository.ALERGIAS);
        prepararSecaoDeLista(view, R.id.campoPreferencia, R.id.chipsPreferencias,
                Arrays.asList(getResources().getStringArray(R.array.preferencias)),
                PerfilRepository.PREFERENCIAS);

        // As alergias vem do catalogo da API, o mesmo do site; ate ele chegar,
        // vale o ultimo guardado (ou a lista reserva).
        catalogo.atualizarEmSegundoPlano(nomes -> {
            View raiz = getView();
            if (raiz != null) {
                trocarOpcoes(raiz.findViewById(R.id.campoAlergia), nomes);
            }
        });
    }

    /**
     * Cada edicao ja salva no aparelho na hora; o envio para a API fica para
     * quando a pessoa sai da tela, juntando todas as edicoes num envio so. Se
     * nada mudou, a sincronizacao nao manda nada.
     */
    @Override
    public void onStop() {
        super.onStop();
        new SincronizacaoRepository(requireContext()).sincronizarEmSegundoPlano();
    }

    private void mostrarUsuario(View view) {
        Usuario usuario = new AutenticacaoRepository().usuarioLogado();

        boolean semNome = usuario == null
                || usuario.getNome() == null || usuario.getNome().trim().isEmpty();
        nomeExibido = semNome ? getString(R.string.perfil_sem_nome) : usuario.getNome();
        emailExibido = usuario == null || usuario.getEmail() == null ? "" : usuario.getEmail();

        ((TextView) view.findViewById(R.id.textNome)).setText(nomeExibido);
        ((TextView) view.findViewById(R.id.textEmail)).setText(emailExibido);
    }

    private void abrirMenuLateral() {
        MenuLateralPerfil.mostrar(requireContext(), nomeExibido, emailExibido,
                this::escolherFoto, this::abrirProdutosEmAnalise, this::voltarAoLogin);
    }

    private void abrirProdutosEmAnalise() {
        startActivity(new Intent(requireContext(), ProdutosEmAnaliseActivity.class));
    }

    private void escolherFoto() {
        new AlertDialog.Builder(requireContext())
                .setItems(new CharSequence[]{
                        getString(R.string.foto_perfil_tirar),
                        getString(R.string.foto_perfil_galeria)
                }, (dialog, opcao) -> {
                    if (opcao == 0) {
                        abrirCamera();
                    } else {
                        pickerGaleria.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    }
                })
                .show();
    }

    private void abrirCamera() {
        try {
            uriCameraPendente = ImagemLocalUtil.criarUriParaCaptura(requireContext());
            pickerCamera.launch(uriCameraPendente);
        } catch (IOException e) {
            Toast.makeText(requireContext(), R.string.foto_perfil_erro, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * A foto nova aparece assim que fica pronta no aparelho; o envio para a
     * API vai em segundo plano e, se falhar, tenta de novo na proxima abertura.
     */
    private void trocarFoto(Uri origem) {
        fotoPerfil.trocar(origem, caminho -> {
            if (caminho == null) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.foto_perfil_erro,
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }
            mostrarFoto(caminho);
            fotoPerfil.enviarEmSegundoPlano();
        });
    }

    /** Pode chegar depois que a tela fechou (a foto vem da API): ai nao faz nada. */
    private void mostrarFoto(@Nullable String caminho) {
        View raiz = getView();
        if (raiz == null) {
            return;
        }
        AvatarPerfilView avatar = raiz.findViewById(R.id.imgAvatar);
        if (caminho == null) {
            avatar.setImageDrawable(null);
            return;
        }
        Coil.imageLoader(requireContext()).enqueue(new ImageRequest.Builder(requireContext())
                .data(caminho)
                // A view posiciona a foto sozinha (ver AvatarPerfilView); FILL
                // faz o Coil abrir a imagem grande o bastante para cobrir o
                // circulo sem ficar borrada.
                .scale(Scale.FILL)
                .target(avatar)
                .build());
    }

    /**
     * Sai da conta e volta para a tela de boas-vindas (Entrar / Cadastrar),
     * limpando a pilha para o voltar do celular nao trazer o perfil de volta.
     * As respostas do questionario continuam no aparelho, guardadas por conta
     * (ver DadosDaConta): entrar de novo com a mesma conta traz tudo de volta.
     */
    private void voltarAoLogin() {
        new AutenticacaoRepository().sair();

        Intent intent = new Intent(requireContext(), BemVindoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void montarAtributos() {
        listaAtributos.removeAllViews();

        for (Atributo atributo : ATRIBUTOS) {
            View card = getLayoutInflater()
                    .inflate(R.layout.item_perfil_atributo, listaAtributos, false);

            ((TextView) card.findViewById(R.id.textRotulo)).setText(atributo.rotulo);
            mostrarValor(card.findViewById(R.id.textValor), valorAtual(atributo));

            ImageButton editar = card.findViewById(R.id.btnEditar);
            editar.setOnClickListener(v -> abrirModal(atributo));

            listaAtributos.addView(card);
        }
    }

    /**
     * As perguntas de varias escolhas guardam so as tags (acneProne,
     * isPregnant...), entao o texto do cartao e montado a partir das opcoes.
     */
    @Nullable
    private String valorAtual(Atributo atributo) {
        if (atributo.exclusivas != null && atributo.opcoes != null) {
            return ModalEscolhaMultipla.rotulos(requireContext(), atributo.opcoes,
                    perfil.getLista(atributo.chave));
        }
        return perfil.getRotulo(atributo.chave);
    }

    private void mostrarValor(TextView campo, String valor) {
        boolean respondido = valor != null && !valor.trim().isEmpty();
        campo.setText(respondido ? valor : getString(R.string.perfil_pendente));
        campo.setTextColor(ContextCompat.getColor(requireContext(),
                respondido ? R.color.roxo_marca : R.color.laranja_pendente));
    }

    /**
     * Abre o modal por cima da propria tela de perfil - nao navega para
     * nenhuma outra Activity, entao nunca da a impressao de "sair" do perfil.
     */
    private void abrirModal(Atributo atributo) {
        if (atributo.exclusivas != null && atributo.opcoes != null) {
            ModalEscolhaMultipla.mostrar(requireContext(), atributo.pergunta,
                    perfil.getLista(atributo.chave), atributo.opcoes, atributo.exclusivas,
                    tags -> {
                        perfil.salvarLista(atributo.chave, tags);
                        montarAtributos();
                    });
            return;
        }

        String tagAtual = perfil.getTag(atributo.chave);

        if (atributo.opcoes == null) {
            ModalFaixaEtaria.mostrar(requireContext(), tagAtual, (tag, rotulo) -> {
                perfil.salvarResposta(atributo.chave, tag, rotulo);
                montarAtributos();
            });
            return;
        }

        ModalEscolhaUnica.mostrar(requireContext(), atributo.pergunta, tagAtual, atributo.opcoes,
                (tag, rotulo) -> {
                    perfil.salvarResposta(atributo.chave, tag, rotulo);
                    montarAtributos();
                });
    }

    /**
     * Alergias e Valores sao editaveis na propria tela: escolher no campo de
     * busca adiciona um chip, e o X remove. Cada mudanca ja salva.
     */
    private void prepararSecaoDeLista(View raiz, int idCampo, int idChips,
                                      List<String> opcoes, String chave) {
        AppCompatAutoCompleteTextView campo = raiz.findViewById(idCampo);
        LinearLayout chips = raiz.findViewById(idChips);

        trocarOpcoes(campo, opcoes);

        for (String salvo : perfil.getLista(chave)) {
            adicionarChip(chips, chave, salvo);
        }

        campo.setOnItemClickListener((pai, v, posicao, id) -> {
            adicionarChip(chips, chave, (String) pai.getItemAtPosition(posicao));
            campo.setText("");
        });
    }

    private void trocarOpcoes(AppCompatAutoCompleteTextView campo, List<String> opcoes) {
        campo.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, opcoes));
    }

    private void adicionarChip(LinearLayout chips, String chave, String item) {
        List<String> atuais = perfil.getLista(chave);
        if (!atuais.contains(item)) {
            atuais.add(item);
            perfil.salvarLista(chave, atuais);
        }

        View chip = getLayoutInflater()
                .inflate(R.layout.item_chip_selecionado, chips, false);
        ((TextView) chip.findViewById(R.id.textChip)).setText(item);

        chip.findViewById(R.id.btnRemoverChip).setOnClickListener(v -> {
            List<String> restantes = perfil.getLista(chave);
            restantes.remove(item);
            perfil.salvarLista(chave, restantes);
            chips.removeView(chip);
        });

        chips.addView(chip);
    }
}
