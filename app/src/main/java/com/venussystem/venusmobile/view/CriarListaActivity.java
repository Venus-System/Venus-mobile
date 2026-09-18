package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.view.util.ImagemLocalUtil;

import java.io.IOException;

public class CriarListaActivity extends AppCompatActivity {

    private EditText editNome;
    private EditText editDescricao;
    private ImageView avatarLista;
    private ColecaoRepository repository;

    @Nullable
    private String caminhoImagemEscolhida;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_criar_lista);

        repository = new ColecaoRepository(this);

        editNome = findViewById(R.id.editNomeLista);
        editDescricao = findViewById(R.id.editDescricaoLista);
        avatarLista = findViewById(R.id.avatarLista);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarLista).setOnClickListener(v -> finish());

        findViewById(R.id.btnEscolherCapa).setOnClickListener(v -> abrirEscolhaDeCapa());

        findViewById(R.id.btnSalvarLista).setOnClickListener(v -> salvar());
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
            caminhoImagemEscolhida = ImagemLocalUtil.salvarCopiaPersistente(this, origem);
            mostrarCapa(caminhoImagemEscolhida);
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

    private void salvar() {
        String nome = editNome.getText().toString().trim();
        if (nome.isEmpty()) {
            editNome.setError(getString(R.string.lista_erro_nome));
            return;
        }

        String descricao = editDescricao.getText().toString().trim();
        Colecao criada = repository.criar(nome, descricao, caminhoImagemEscolhida);

        Intent intent = new Intent(this, DetalheListaActivity.class);
        intent.putExtra(DetalheListaActivity.EXTRA_ID, criada.getId());
        intent.putExtra(DetalheListaActivity.EXTRA_NOME, criada.getName());
        intent.putExtra(DetalheListaActivity.EXTRA_DESCRICAO, criada.getDescricao());
        intent.putExtra(DetalheListaActivity.EXTRA_IMAGEM, criada.getImageUrl());
        startActivity(intent);

        // Sai da tela de criacao: voltar do detalhe tem que cair nas listas,
        // nao no formulario que o usuario acabou de preencher.
        finish();
    }
}
