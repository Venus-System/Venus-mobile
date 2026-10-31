package com.venussystem.venusmobile.view.fragment;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.adapter.ConversaAdapter;
import com.venussystem.venusmobile.view.componente.VeninhaView;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;
import com.venussystem.venusmobile.viewmodel.AssistenteViewModel;

public class AssistenteFragment extends Fragment {

    public static final String EXTRA_PRODUTO_ID = "assistente_produto_id";
    public static final String EXTRA_PRODUTO_NOME = "assistente_produto_nome";
    public static final String EXTRA_PRODUTO_MARCA = "assistente_produto_marca";
    public static final String EXTRA_PRODUTO_NOTA = "assistente_produto_nota";
    public static final String EXTRA_PRODUTO_IMAGEM = "assistente_produto_imagem";

    private static final String ARG_COM_VOLTAR = "assistente_com_voltar";

    private static final int SEM_NOTA = -1;

    private static final float ALFA_DESABILITADO = 0.4f;

    private static final int MAXIMO_LINHAS_CAMPO = 4;

    private static final long DURACAO_SUBIDA_MS = 300L;

    private static final float ESCALA_MAXIMA_CENTRO = 2.4f;

    private static final float FRACAO_DO_ESPACO_NO_CENTRO = 0.45f;

    public static Bundle extrasDoProduto(@Nullable Produto produto) {
        Bundle extras = new Bundle();
        if (produto == null) {
            return extras;
        }
        Long id = produto.getId();
        Integer nota = produto.getOverallScore();
        extras.putLong(EXTRA_PRODUTO_ID, id == null ? -1L : id);
        extras.putString(EXTRA_PRODUTO_NOME, produto.getName());
        extras.putString(EXTRA_PRODUTO_MARCA, produto.getBrandName());
        extras.putInt(EXTRA_PRODUTO_NOTA, nota == null ? SEM_NOTA : nota);
        extras.putString(EXTRA_PRODUTO_IMAGEM, produto.getImageUrl());
        return extras;
    }

    public static AssistenteFragment doProduto(@Nullable Bundle extrasDoProduto) {
        Bundle args = extrasDoProduto == null ? new Bundle() : new Bundle(extrasDoProduto);
        args.putBoolean(ARG_COM_VOLTAR, true);
        AssistenteFragment fragment = new AssistenteFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private AssistenteViewModel viewModel;
    private ConversaAdapter adapter;
    private RecyclerView lista;
    private EditText campo;
    private View botaoEnviar;
    private View botaoNovaConversa;
    private LinearLayout linhaSugestoes;
    private VeninhaView veninha;
    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener aoMudarLayout;

    private boolean jaRolou = false;
    private boolean semMensagens = true;
    private boolean tecladoAberto = false;
    private boolean veninhaPosicionada = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        campo = view.findViewById(R.id.campoMensagem);
        lista = view.findViewById(R.id.listaMensagens);
        botaoEnviar = view.findViewById(R.id.btnEnviar);
        botaoNovaConversa = view.findViewById(R.id.btnNovaConversa);
        linhaSugestoes = view.findViewById(R.id.linhaSugestoes);
        veninha = view.findViewById(R.id.veninha);

        aoMudarLayout = () -> ajustarVeninhaAoTeclado(view);
        view.getViewTreeObserver().addOnGlobalLayoutListener(aoMudarLayout);

        campo.setHorizontallyScrolling(false);
        campo.setMaxLines(MAXIMO_LINHAS_CAMPO);

        adapter = new ConversaAdapter();
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        boolean comVoltar = argumentos().getBoolean(ARG_COM_VOLTAR, false);
        View btnVoltar = view.findViewById(R.id.btnVoltar);
        btnVoltar.setVisibility(comVoltar ? View.VISIBLE : View.GONE);
        btnVoltar.setOnClickListener(v -> requireActivity().finish());
        botaoEnviar.setOnClickListener(v -> enviar());
        botaoNovaConversa.setOnClickListener(v -> confirmarNovaConversa());

        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            return false;
        });

        Produto produto = produtoDe(argumentos());

        viewModel = new ViewModelProvider(requireActivity()).get(AssistenteViewModel.class);
        viewModel.sincronizarComSalva();
        if (produto != null) {
            viewModel.definirProduto(produto.getName(), produto.getBrandName());
        }

        mostrarProduto(view, produto);
        montarSugestoes(produto != null);

        viewModel.getMensagens().observe(getViewLifecycleOwner(), mensagens -> {
            adapter.definir(mensagens);
            semMensagens = mensagens.isEmpty();
            posicionarVeninha(view);
            if (mensagens.isEmpty()) {
                return;
            }
            if (jaRolou) {
                lista.smoothScrollToPosition(mensagens.size() - 1);
            } else {
                lista.scrollToPosition(mensagens.size() - 1);
                jaRolou = true;
            }
        });
        viewModel.getAguardando().observe(getViewLifecycleOwner(), this::mostrarAguardando);
        viewModel.getPerguntaQueFalhou().observe(getViewLifecycleOwner(), this::devolverAoCampo);
        viewModel.getHumor().observe(getViewLifecycleOwner(), veninha::setHumor);
        viewModel.getFala().observe(getViewLifecycleOwner(), this::falar);
    }

    @Override
    public void onDestroyView() {
        View raiz = getView();
        if (raiz != null && aoMudarLayout != null) {
            raiz.getViewTreeObserver().removeOnGlobalLayoutListener(aoMudarLayout);
        }
        aoMudarLayout = null;
        super.onDestroyView();
    }

    @NonNull
    private Bundle argumentos() {
        Bundle args = getArguments();
        return args == null ? new Bundle() : args;
    }

    @Nullable
    private static Produto produtoDe(Bundle extras) {
        String nome = extras.getString(EXTRA_PRODUTO_NOME);
        if (nome == null || nome.trim().isEmpty()) {
            return null;
        }
        long id = extras.getLong(EXTRA_PRODUTO_ID, -1L);
        int nota = extras.getInt(EXTRA_PRODUTO_NOTA, SEM_NOTA);
        return new Produto(
                id > 0 ? id : null,
                nome,
                extras.getString(EXTRA_PRODUTO_MARCA),
                nota < 0 ? null : nota,
                extras.getString(EXTRA_PRODUTO_IMAGEM),
                null,
                null);
    }

    private void mostrarProduto(View view, @Nullable Produto produto) {
        View card = view.findViewById(R.id.cardProduto);
        if (produto == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        TextView nome = view.findViewById(R.id.textNomeProduto);
        nome.setText(produto.getName());

        TextView marca = view.findViewById(R.id.textMarcaProduto);
        String textoMarca = produto.getBrandName();
        boolean temMarca = textoMarca != null && !textoMarca.trim().isEmpty();
        marca.setText(temMarca ? textoMarca : "");
        marca.setVisibility(temMarca ? View.VISIBLE : View.GONE);

        TextView nota = view.findViewById(R.id.textNotaProduto);
        Integer valor = produto.getOverallScore();
        nota.setText(valor == null ? getString(R.string.nota_indisponivel) : String.valueOf(valor));
        nota.setTextColor(ContextCompat.getColor(requireContext(), NotaProdutoUtil.corPara(valor)));
        nota.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), NotaProdutoUtil.fundoPara(valor))));

        ImageView imagem = view.findViewById(R.id.imgProduto);
        ProdutoImagemLoader.carregar(requireContext(), imagem, produto, R.drawable.bg_foto_historico);
    }

    private void montarSugestoes(boolean doProduto) {
        int[] sugestoes = doProduto
                ? new int[]{
                        R.string.assistente_sugestao_produto_um,
                        R.string.assistente_sugestao_produto_dois,
                        R.string.assistente_sugestao_produto_tres,
                }
                : new int[]{
                        R.string.assistente_sugestao_geral_um,
                        R.string.assistente_sugestao_geral_dois,
                        R.string.assistente_sugestao_geral_tres,
                };

        for (int sugestao : sugestoes) {
            TextView chip = (TextView) getLayoutInflater()
                    .inflate(R.layout.item_chip_sugestao, linhaSugestoes, false);
            chip.setText(sugestao);
            chip.setOnClickListener(v -> viewModel.perguntar(chip.getText().toString()));
            linhaSugestoes.addView(chip);
        }
    }

    private void enviar() {
        if (viewModel.perguntar(campo.getText().toString())) {
            campo.setText("");
        }
    }

    private void devolverAoCampo(@Nullable String pergunta) {
        if (pergunta == null) {
            return;
        }
        if (campo.getText().toString().trim().isEmpty()) {
            campo.setText(pergunta);
            campo.setSelection(campo.getText().length());
        }
        viewModel.esquecerPerguntaQueFalhou();
    }

    private void falar(@Nullable String texto) {
        if (texto == null) {
            return;
        }
        veninha.falar(texto);
        viewModel.esquecerFala();
    }

    private void ajustarVeninhaAoTeclado(View raiz) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(raiz);
        boolean comTeclado = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        tecladoAberto = comTeclado;
        int tamanho = raiz.getResources().getDimensionPixelSize(
                comTeclado ? R.dimen.veninha_chat_teclado : R.dimen.veninha_chat);
        ViewGroup.LayoutParams params = veninha.getLayoutParams();
        if (params.height != tamanho) {
            params.width = tamanho;
            params.height = tamanho;
            veninha.setLayoutParams(params);
            return;
        }
        posicionarVeninha(raiz);
    }

    private void posicionarVeninha(View raiz) {
        if (veninha == null || veninha.getHeight() == 0
                || veninha.getHeight() != veninha.getLayoutParams().height) {
            return;
        }
        View limiteInferior = raiz.findViewById(R.id.rolagemSugestoes);
        float alvo = 0f;
        float escala = 1f;
        if (semMensagens && !tecladoAberto) {
            int espaco = limiteInferior.getTop() - veninha.getTop();
            alvo = Math.max(0, (espaco - veninha.getHeight()) / 2f);
            escala = Math.max(1f, Math.min(ESCALA_MAXIMA_CENTRO,
                    espaco * FRACAO_DO_ESPACO_NO_CENTRO / veninha.getHeight()));
        }
        if (veninha.getTranslationY() == alvo && veninha.getScaleX() == escala) {
            return;
        }
        veninha.animate().cancel();
        if (!veninhaPosicionada) {
            veninha.setTranslationY(alvo);
            veninha.setScaleX(escala);
            veninha.setScaleY(escala);
            veninhaPosicionada = true;
            return;
        }
        veninha.animate()
                .translationY(alvo)
                .scaleX(escala)
                .scaleY(escala)
                .setDuration(DURACAO_SUBIDA_MS)
                .start();
    }

    private void confirmarNovaConversa() {
        if (Boolean.TRUE.equals(viewModel.getAguardando().getValue())) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assistente_nova_conversa_titulo)
                .setMessage(R.string.assistente_nova_conversa_texto)
                .setNegativeButton(R.string.lista_cancelar, null)
                .setPositiveButton(R.string.assistente_nova_conversa_confirmar,
                        (dialogo, botao) -> viewModel.novaConversa())
                .show();
    }

    private void mostrarAguardando(@NonNull Boolean aguardando) {
        float alfa = aguardando ? ALFA_DESABILITADO : 1f;

        botaoEnviar.setEnabled(!aguardando);
        botaoEnviar.setAlpha(alfa);
        botaoNovaConversa.setEnabled(!aguardando);
        botaoNovaConversa.setAlpha(alfa);
        for (int i = 0; i < linhaSugestoes.getChildCount(); i++) {
            View chip = linhaSugestoes.getChildAt(i);
            chip.setEnabled(!aguardando);
            chip.setAlpha(alfa);
        }
    }
}
