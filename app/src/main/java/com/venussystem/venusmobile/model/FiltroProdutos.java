package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class FiltroProdutos {

    private static final FiltroProdutos VAZIO = new FiltroProdutos(
            Collections.<Long>emptySet(), Collections.<Long>emptySet(),
            Collections.<FaixaNota>emptySet(), false);

    private final Set<Long> marcas;
    private final Set<Long> categorias;
    private final Set<FaixaNota> faixas;
    private final boolean soBrasileiras;

    public FiltroProdutos(@NonNull Set<Long> marcas, @NonNull Set<Long> categorias,
                          @NonNull Set<FaixaNota> faixas, boolean soBrasileiras) {
        this.marcas = Collections.unmodifiableSet(new HashSet<>(marcas));
        this.categorias = Collections.unmodifiableSet(new HashSet<>(categorias));
        this.faixas = Collections.unmodifiableSet(
                faixas.isEmpty() ? EnumSet.noneOf(FaixaNota.class) : EnumSet.copyOf(faixas));
        this.soBrasileiras = soBrasileiras;
    }

    public static FiltroProdutos vazio() {
        return VAZIO;
    }

    public Set<Long> getMarcas() {
        return marcas;
    }

    public Set<Long> getCategorias() {
        return categorias;
    }

    public Set<FaixaNota> getFaixas() {
        return faixas;
    }

    public boolean isSoBrasileiras() {
        return soBrasileiras;
    }

    public boolean isAtivo() {
        return !marcas.isEmpty() || !categorias.isEmpty() || !faixas.isEmpty() || soBrasileiras;
    }

    public boolean aceita(@NonNull Produto produto) {
        if (!marcas.isEmpty() && !marcas.contains(produto.getBrandId())) {
            return false;
        }
        if (!categorias.isEmpty() && !categorias.contains(produto.getCategoryId())) {
            return false;
        }
        if (!faixas.isEmpty() && !faixas.contains(FaixaNota.de(produto.getOverallScore()))) {
            return false;
        }
        return !soBrasileiras || Boolean.TRUE.equals(produto.getBrazilianBrand());
    }

    public FiltroProdutos comMarca(long id, boolean marcada) {
        return new FiltroProdutos(alternar(marcas, id, marcada), categorias, faixas, soBrasileiras);
    }

    public FiltroProdutos comCategoria(long id, boolean marcada) {
        return new FiltroProdutos(marcas, alternar(categorias, id, marcada), faixas, soBrasileiras);
    }

    public FiltroProdutos comFaixa(FaixaNota faixa, boolean marcada) {
        return new FiltroProdutos(marcas, categorias, alternar(faixas, faixa, marcada), soBrasileiras);
    }

    public FiltroProdutos comSoBrasileiras(boolean valor) {
        return new FiltroProdutos(marcas, categorias, faixas, valor);
    }

    private static <T> Set<T> alternar(Set<T> origem, T item, boolean marcado) {
        Set<T> copia = new HashSet<>(origem);
        if (marcado) {
            copia.add(item);
        } else {
            copia.remove(item);
        }
        return copia;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof FiltroProdutos)) {
            return false;
        }
        FiltroProdutos f = (FiltroProdutos) outro;
        return soBrasileiras == f.soBrasileiras && marcas.equals(f.marcas)
                && categorias.equals(f.categorias) && faixas.equals(f.faixas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marcas, categorias, faixas, soBrasileiras);
    }
}
