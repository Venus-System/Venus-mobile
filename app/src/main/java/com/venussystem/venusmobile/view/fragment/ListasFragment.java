package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.repository.ListaItemRepository;
import com.venussystem.venusmobile.view.CriarListaActivity;
import com.venussystem.venusmobile.view.DetalheListaActivity;
import com.venussystem.venusmobile.model.Colecao;
import com.venussystem.venusmobile.view.adapter.ColecaoAdapter;

public class ListasFragment extends Fragment {
    private ColecaoRepository repository;
    private ListaItemRepository itemRepository;
    private ColecaoAdapter adapter;
    private RecyclerView lista;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_listas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new ColecaoRepository(requireContext());
        itemRepository = new ListaItemRepository(requireContext());
        adapter = new ColecaoAdapter(this::abrirLista);

        lista = view.findViewById(R.id.listaColecoes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);
        new ItemTouchHelper(new SwipeParaExcluir()).attachToRecyclerView(lista);

        view.findViewById(R.id.btnNovaLista).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CriarListaActivity.class)));

        carregar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recarrega ao voltar de CriarListaActivity ou DetalheListaActivity: a
        // view do fragment sobrevive nessa navegacao, entao sem isto uma lista
        // criada/editada/excluida so apareceria depois de sair e voltar desta aba.
        carregar();
    }

    private void carregar() {
        repository.minhasListas().observe(getViewLifecycleOwner(), adapter::atualizar);
    }

    private void abrirLista(Colecao colecao) {
        Intent intent = new Intent(requireContext(), DetalheListaActivity.class);
        intent.putExtra(DetalheListaActivity.EXTRA_ID, colecao.getId());
        intent.putExtra(DetalheListaActivity.EXTRA_NOME, colecao.getName());
        intent.putExtra(DetalheListaActivity.EXTRA_DESCRICAO, colecao.getDescricao());
        intent.putExtra(DetalheListaActivity.EXTRA_IMAGEM, colecao.getImageUrl());
        startActivity(intent);
    }

    /**
     * Arrastar uma lista pra esquerda pede confirmacao antes de excluir - se o
     * usuario cancelar, carregar() de novo traz a linha de volta, porque o
     * proprio ItemTouchHelper ja a tirou de tela ao terminar o gesto.
     */
    private class SwipeParaExcluir extends ItemTouchHelper.SimpleCallback {

        // Mesmo raio do bg_card_lista - senao o fundo vermelho aparece com
        // canto reto ao lado do canto arredondado do card por cima dele.
        private static final float RAIO_CARD_DP = 14f;

        private final Paint pincelFundo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF areaFundo = new RectF();

        SwipeParaExcluir() {
            super(0, ItemTouchHelper.LEFT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                               @NonNull RecyclerView.ViewHolder viewHolder,
                               @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            Colecao colecao = adapter.emPosicao(viewHolder.getBindingAdapterPosition());

            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.lista_excluir_titulo)
                    .setMessage(R.string.lista_excluir_mensagem)
                    .setPositiveButton(R.string.acao_excluir, (dialog, which) -> {
                        repository.excluir(colecao.getId());
                        itemRepository.excluirTodos(colecao.getId());
                        carregar();
                    })
                    .setNegativeButton(R.string.lista_cancelar, (dialog, which) -> carregar())
                    .setOnCancelListener(dialog -> carregar())
                    .show();
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                 int actionState, boolean isCurrentlyActive) {
            View item = viewHolder.itemView;

            if (dX < 0) {
                float raioPx = RAIO_CARD_DP * getResources().getDisplayMetrics().density;
                pincelFundo.setColor(ContextCompat.getColor(requireContext(), R.color.nota_ruim));
                areaFundo.set(item.getRight() + dX, item.getTop(), item.getRight(), item.getBottom());
                c.drawRoundRect(areaFundo, raioPx, raioPx, pincelFundo);

                Drawable icone = ContextCompat.getDrawable(requireContext(), R.drawable.ic_excluir);
                if (icone != null) {
                    int margem = (item.getHeight() - icone.getIntrinsicHeight()) / 2;
                    int topo = item.getTop() + margem;
                    int fimIcone = item.getRight() - margem;
                    icone.setBounds(fimIcone - icone.getIntrinsicWidth(), topo,
                            fimIcone, topo + icone.getIntrinsicHeight());
                    icone.draw(c);
                }
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }
}
