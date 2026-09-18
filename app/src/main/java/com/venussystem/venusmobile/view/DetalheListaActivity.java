package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.repository.ListaItemRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.view.adapter.ItemListaAdapter;
import com.venussystem.venusmobile.view.util.ImagemLocalUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma lista do proprio usuario. Recebe pelo Intent o que ja se sabe dela; os
 * produtos vem do catalogo ja em memoria (ver ProdutoRepository) filtrados
 * pelos ids guardados no aparelho para esta lista (ver ListaItemRepository) -
 * a API tambem teria como fazer isso (/api/user-list-items), mas depende de
 * um userId que o app ainda nao sabe obter.
 */
public class DetalheListaActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_NOME = "nome";
    public static final String EXTRA_DESCRICAO = "descricao";
    public static final String EXTRA_IMAGEM = "imagem";

    private TextView textNomeLista;
    private TextView textDescricaoLista;
    private ImageView avatarLista;
    private View blocoVazio;
    private RecyclerView listaProdutos;
    private ItemListaAdapter adapter;

    private ColecaoRepository colecaoRepository;
    private ListaItemRepository itemRepository;
    private ProdutoRepository produtoRepository;
    private long listaId;
    private String nomeAtual;
    private String descricaoAtual;

    @Nullable
    private Uri uriCameraPendente;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickerGaleria =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            processarImagemEscolhida(uri);
                        }
                    });

    private final ActivityResultLauncher<Uri> pickerCamera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), sucesso -> {
                if (Boolean.TRUE.equals(sucesso) && uriCameraPendente != null) {
                    processarImagemEscolhida(uriCameraPendente);
                }
            });

    private final ActivityResultLauncher<Intent> escolherProdutoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() != RESULT_OK || resultado.getData() == null) {
                    return;
                }
                long produtoId = resultado.getData()
                        .getLongExtra(EscolherProdutoActivity.EXTRA_PRODUTO_ID, -1);
                if (produtoId != -1) {
                    itemRepository.adicionar(listaId, produtoId);
                    carregarItens();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_detalhe_lista);

        colecaoRepository = new ColecaoRepository(this);
        itemRepository = new ListaItemRepository(this);
        produtoRepository = new ProdutoRepository();
        listaId = getIntent().getLongExtra(EXTRA_ID, -1);

        textNomeLista = findViewById(R.id.textNomeLista);
        textDescricaoLista = findViewById(R.id.textDescricaoLista);
        avatarLista = findViewById(R.id.avatarLista);
        blocoVazio = findViewById(R.id.blocoVazio);
        listaProdutos = findViewById(R.id.listaProdutosDaLista);
        listaProdutos.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ItemListaAdapter(this::abrirProduto, this::removerProduto);
        listaProdutos.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnMaisOpcoes).setOnClickListener(this::mostrarMenuOpcoes);
        findViewById(R.id.btnEscolherCapa).setOnClickListener(v -> abrirEscolhaDeCapa());
        findViewById(R.id.btnAdicionarProduto).setOnClickListener(v ->
                escolherProdutoLauncher.launch(new Intent(this, EscolherProdutoActivity.class)));

        preencherCabecalho();
        carregarItens();
    }

    private void preencherCabecalho() {
        nomeAtual = getIntent().getStringExtra(EXTRA_NOME);
        descricaoAtual = getIntent().getStringExtra(EXTRA_DESCRICAO);

        TextView autor = findViewById(R.id.textAutorLista);
        autor.setText(getString(R.string.lista_criada_por, nomeDoUsuario()));

        atualizarNomeNaTela();
        atualizarDescricaoNaTela();

        String imagem = getIntent().getStringExtra(EXTRA_IMAGEM);
        if (imagem != null) {
            mostrarCapa(imagem);
        }
    }

    private void atualizarNomeNaTela() {
        textNomeLista.setText(nomeAtual);
    }

    private void atualizarDescricaoNaTela() {
        boolean temDescricao = descricaoAtual != null && !descricaoAtual.trim().isEmpty();
        textDescricaoLista.setText(temDescricao ? descricaoAtual : "");
        textDescricaoLista.setVisibility(temDescricao ? View.VISIBLE : View.GONE);
    }

    private String nomeDoUsuario() {
        Usuario usuario = new AutenticacaoRepository().usuarioLogado();
        if (usuario == null || usuario.getNome() == null || usuario.getNome().trim().isEmpty()) {
            return getString(R.string.perfil_sem_nome);
        }
        return usuario.getNome();
    }

    private void mostrarMenuOpcoes(View ancora) {
        PopupMenu menu = new PopupMenu(this, ancora);
        menu.getMenuInflater().inflate(R.menu.menu_lista_opcoes, menu.getMenu());

        MenuItem opcaoExcluir = menu.getMenu().findItem(R.id.opcaoExcluirLista);
        SpannableString textoExcluir = new SpannableString(opcaoExcluir.getTitle());
        textoExcluir.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.nota_ruim)),
                0, textoExcluir.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        opcaoExcluir.setTitle(textoExcluir);

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.opcaoEditarNome) {
                mostrarDialogoEditarNome();
                return true;
            }
            if (id == R.id.opcaoEditarDescricao) {
                mostrarDialogoEditarDescricao();
                return true;
            }
            if (id == R.id.opcaoExcluirLista) {
                confirmarExclusao();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void mostrarDialogoEditarNome() {
        View conteudo = getLayoutInflater().inflate(R.layout.dialog_editar_texto, null);
        EditText campo = conteudo.findViewById(R.id.editTexto);
        campo.setText(nomeAtual);
        if (nomeAtual != null) {
            campo.setSelection(nomeAtual.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lista_editar_nome)
                .setView(conteudo)
                .setPositiveButton(R.string.lista_salvar, null)
                .setNegativeButton(R.string.lista_cancelar, null)
                .create();

        // Botao positivo tratado a mao (sem passar o listener pro Builder):
        // um nome vazio precisa manter o dialog aberto mostrando o erro, e o
        // listener do Builder sempre fecha o dialog depois de rodar.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String novoNome = campo.getText().toString().trim();
            if (novoNome.isEmpty()) {
                campo.setError(getString(R.string.lista_erro_nome));
                return;
            }
            nomeAtual = novoNome;
            colecaoRepository.renomear(listaId, novoNome);
            atualizarNomeNaTela();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void mostrarDialogoEditarDescricao() {
        View conteudo = getLayoutInflater().inflate(R.layout.dialog_editar_texto, null);
        EditText campo = conteudo.findViewById(R.id.editTexto);
        campo.setText(descricaoAtual);
        if (descricaoAtual != null) {
            campo.setSelection(descricaoAtual.length());
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.lista_editar_descricao)
                .setView(conteudo)
                .setPositiveButton(R.string.lista_salvar, (dialog, which) -> {
                    descricaoAtual = campo.getText().toString().trim();
                    colecaoRepository.atualizarDescricao(listaId, descricaoAtual);
                    atualizarDescricaoNaTela();
                })
                .setNegativeButton(R.string.lista_cancelar, null)
                .show();
    }

    private void confirmarExclusao() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.lista_excluir_titulo)
                .setMessage(R.string.lista_excluir_mensagem)
                .setPositiveButton(R.string.acao_excluir, (dialog, which) -> {
                    colecaoRepository.excluir(listaId);
                    itemRepository.excluirTodos(listaId);
                    finish();
                })
                .setNegativeButton(R.string.lista_cancelar, null)
                .show();
    }

    /**
     * Le os ids guardados para esta lista e busca cada produto no catalogo ja
     * em memoria. Um id sem produto correspondente (catalogo ainda carregando,
     * ou produto removido) e simplesmente ignorado.
     */
    private void carregarItens() {
        List<Produto> produtos = new ArrayList<>();
        for (Long id : itemRepository.getProdutoIds(listaId)) {
            Produto produto = produtoRepository.buscarNoCache(id);
            if (produto != null) {
                produtos.add(produto);
            }
        }

        adapter.atualizar(produtos);
        boolean vazio = produtos.isEmpty();
        blocoVazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        listaProdutos.setVisibility(vazio ? View.GONE : View.VISIBLE);
    }

    private void abrirProduto(Produto produto) {
        Intent intent = new Intent(this, DetalheProdutoActivity.class);
        intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
        startActivity(intent);
    }

    private void removerProduto(Produto produto) {
        itemRepository.remover(listaId, produto.getId());
        carregarItens();
    }

    private void abrirEscolhaDeCapa() {
        new AlertDialog.Builder(this)
                .setItems(new CharSequence[]{
                        getString(R.string.lista_capa_tirar_foto),
                        getString(R.string.lista_capa_galeria)
                }, (dialog, opcao) -> {
                    if (opcao == 0) {
                        abrirCamera();
                    } else {
                        abrirGaleria();
                    }
                })
                .show();
    }

    private void abrirGaleria() {
        pickerGaleria.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void abrirCamera() {
        try {
            uriCameraPendente = ImagemLocalUtil.criarUriParaCaptura(this);
            pickerCamera.launch(uriCameraPendente);
        } catch (IOException e) {
            Toast.makeText(this, R.string.lista_capa_erro, Toast.LENGTH_SHORT).show();
        }
    }

    private void processarImagemEscolhida(Uri origem) {
        try {
            String caminho = ImagemLocalUtil.salvarCopiaPersistente(this, origem);
            mostrarCapa(caminho);
            if (listaId != -1) {
                colecaoRepository.atualizarImagem(listaId, caminho);
            }
        } catch (IOException e) {
            Toast.makeText(this, R.string.lista_capa_erro, Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarCapa(String caminhoImagem) {
        avatarLista.setPadding(0, 0, 0, 0);
        avatarLista.setScaleType(ImageView.ScaleType.CENTER_CROP);

        ImageLoader carregador = Coil.imageLoader(this);
        carregador.enqueue(new ImageRequest.Builder(this)
                .data(caminhoImagem)
                .target(avatarLista)
                .build());
    }
}
