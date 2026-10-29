package com.venussystem.venusmobile.view.componente;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.HumorDaVeninha;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class VeninhaView extends View {
    private static final long INTERVALO_MS = 60;
    private static final long DURACAO_MAXIMA_MS = 3000;
    private static final int LETRAS_POR_SILABA = 3;
    private static final int PASSOS_MAXIMOS = (int) (DURACAO_MAXIMA_MS / INTERVALO_MS / 2) * 2;

    private static final long INTERVALO_INTERROGACAO_MS = 400;

    private static final float[] INTERROGACAO_PARADA = {0.8692f, 0.1187f, 0.2101f};
    private static final float[][] INTERROGACOES = {
            {0.810f, 0.117f, 0.105f},
            {0.882f, 0.097f, 0.105f},
            {0.954f, 0.078f, 0.105f},
    };

    private static final class Boca {
        @DrawableRes
        final int imagem;
        final float x;
        final float y;
        final float largura;
        final float giro;

        Boca(@DrawableRes int imagem, float x, float y, float largura, float giro) {
            this.imagem = imagem;
            this.x = x;
            this.y = y;
            this.largura = largura;
            this.giro = giro;
        }
    }

    private static final class Rosto {
        @DrawableRes
        final int cara;
        final Boca aberta;
        final Boca fechada;
        final boolean abertaQuieta;
        final boolean abertaSemMovimento;

        Rosto(@DrawableRes int cara, Boca aberta, Boca fechada,
              boolean abertaQuieta, boolean abertaSemMovimento) {
            this.cara = cara;
            this.aberta = aberta;
            this.fechada = fechada;
            this.abertaQuieta = abertaQuieta;
            this.abertaSemMovimento = abertaSemMovimento;
        }
    }

    private static final Map<HumorDaVeninha, Rosto> ROSTOS = new EnumMap<>(HumorDaVeninha.class);

    static {
        ROSTOS.put(HumorDaVeninha.FELIZ, new Rosto(R.drawable.veninha_cara_feliz,
                new Boca(R.drawable.veninha_boca_aberta_estreita, 0.443f, 0.5813f, 0.068f, 0f),
                new Boca(R.drawable.veninha_boca_fechada_feliz, 0.443f, 0.56f, 0.08f, 0f),
                false, true));
        ROSTOS.put(HumorDaVeninha.CHORANDO, new Rosto(R.drawable.veninha_cara_chorando,
                new Boca(R.drawable.veninha_boca_aberta_larga, 0.5913f, 0.4953f, 0.083f, 0f),
                new Boca(R.drawable.veninha_boca_fechada_triste, 0.5913f, 0.4905f, 0.0726f, 0f),
                false, false));
        ROSTOS.put(HumorDaVeninha.CONFUSA, new Rosto(R.drawable.veninha_cara_confusa,
                new Boca(R.drawable.veninha_boca_aberta_larga, 0.5f, 0.5767f, 0.1042f, 0f),
                new Boca(R.drawable.veninha_boca_fechada, 0.5f, 0.5767f, 0.1042f, 17f),
                true, true));
        ROSTOS.put(HumorDaVeninha.PENSANDO, new Rosto(R.drawable.veninha_cara_pensando,
                new Boca(R.drawable.veninha_boca_aberta_larga, 0.5407f, 0.5963f, 0.0581f, 0f),
                new Boca(R.drawable.veninha_boca_triste, 0.5407f, 0.5963f, 0.0465f, 0f),
                false, true));
    }

    private final SparseArray<Drawable> imagens = new SparseArray<>();
    private final Runnable passoDaFala = this::darPasso;
    private final Runnable passoDaInterrogacao = this::darPassoDaInterrogacao;

    @Nullable
    private final AnimatedImageDrawable pulo;

    private HumorDaVeninha humor = HumorDaVeninha.FELIZ;
    private int passo = 0;
    private int totalDePassos = 0;
    private int passoInterrogacao = 0;
    private boolean interrogacaoAndando = false;
    private boolean visivel = false;

    public VeninhaView(@NonNull Context context) {
        this(context, null);
    }

    public VeninhaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        pulo = isInEditMode() ? null : carregarPulo();
        if (pulo != null) {
            pulo.setCallback(this);
        }
    }

    public void setHumor(@NonNull HumorDaVeninha novo) {
        if (novo == humor) {
            return;
        }
        humor = novo;
        atualizarAnimacoes();
    }

    public void falar(@Nullable String texto) {
        calar();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        totalDePassos = passosDaFala(texto);
        if (totalDePassos > 0) {
            postDelayed(passoDaFala, INTERVALO_MS);
        }
        atualizarAnimacoes();
    }

    public void calar() {
        removeCallbacks(passoDaFala);
        passo = 0;
        totalDePassos = 0;
        atualizarAnimacoes();
    }

    @VisibleForTesting
    static boolean pula(HumorDaVeninha humor, boolean falando, boolean animacoesLigadas) {
        return humor == HumorDaVeninha.FELIZ && !falando && animacoesLigadas;
    }

    @VisibleForTesting
    static int interrogacoesVisiveis(int passo) {
        return passo % (INTERROGACOES.length + 1);
    }

    @VisibleForTesting
    static int passosDaFala(@Nullable String texto) {
        if (texto == null) {
            return 0;
        }
        long letras = texto.codePoints().filter(Character::isLetter).count();
        long silabas = (letras + LETRAS_POR_SILABA - 1) / LETRAS_POR_SILABA;
        return (int) Math.min(silabas * 2, PASSOS_MAXIMOS);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visivel = isVisible;
        atualizarAnimacoes();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(passoDaFala);
        visivel = false;
        atualizarAnimacoes();
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable quem) {
        return quem == pulo || super.verifyDrawable(quem);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (pulo != null && pulandoAgora()) {
            if (encaixar(pulo)) {
                pulo.draw(canvas);
            }
            return;
        }

        Rosto rosto = ROSTOS.get(humor);
        Drawable cara = imagem(rosto.cara);
        if (!encaixar(cara)) {
            return;
        }
        cara.draw(canvas);

        Rect area = cara.getBounds();
        float esquerda = area.left;
        float topo = area.top;
        float larguraCara = area.width();
        float alturaCara = area.height();

        Boca boca = bocaAberta(rosto) ? rosto.aberta : rosto.fechada;
        Drawable imagemBoca = imagem(boca.imagem);
        float larguraBoca = boca.largura * larguraCara;
        float alturaBoca = larguraBoca * imagemBoca.getIntrinsicHeight()
                / imagemBoca.getIntrinsicWidth();
        float centroX = esquerda + boca.x * larguraCara;
        float centroY = topo + boca.y * alturaCara;

        canvas.save();
        canvas.rotate(boca.giro, centroX, centroY);
        imagemBoca.setBounds(Math.round(centroX - larguraBoca / 2f),
                Math.round(centroY - alturaBoca / 2f),
                Math.round(centroX + larguraBoca / 2f),
                Math.round(centroY + alturaBoca / 2f));
        imagemBoca.draw(canvas);
        canvas.restore();

        if (humor == HumorDaVeninha.PENSANDO) {
            desenharInterrogacoes(canvas, area);
        }
    }

    private boolean bocaAberta(Rosto rosto) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return rosto.abertaSemMovimento;
        }
        boolean falando = passo < totalDePassos;
        return falando ? passo % 2 == 0 : rosto.abertaQuieta;
    }

    private void darPasso() {
        passo++;
        if (passo < totalDePassos) {
            postDelayed(passoDaFala, INTERVALO_MS);
        }
        atualizarAnimacoes();
    }

    private boolean pulandoAgora() {
        return pula(humor, passo < totalDePassos, ValueAnimator.areAnimatorsEnabled());
    }

    private void atualizarAnimacoes() {
        if (pulo != null) {
            if (visivel && pulandoAgora()) {
                pulo.start();
            } else {
                pulo.stop();
            }
        }

        boolean interrogar = visivel && humor == HumorDaVeninha.PENSANDO
                && ValueAnimator.areAnimatorsEnabled();
        if (interrogar && !interrogacaoAndando) {
            interrogacaoAndando = true;
            passoInterrogacao = 0;
            postDelayed(passoDaInterrogacao, INTERVALO_INTERROGACAO_MS);
        } else if (!interrogar && interrogacaoAndando) {
            interrogacaoAndando = false;
            removeCallbacks(passoDaInterrogacao);
        }
        invalidate();
    }

    private void darPassoDaInterrogacao() {
        passoInterrogacao++;
        postDelayed(passoDaInterrogacao, INTERVALO_INTERROGACAO_MS);
        invalidate();
    }

    private void desenharInterrogacoes(Canvas canvas, Rect cara) {
        Drawable interrogacao = imagem(R.drawable.veninha_interrogacao);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            desenharInterrogacao(canvas, interrogacao, cara, INTERROGACAO_PARADA);
            return;
        }
        int visiveis = interrogacoesVisiveis(passoInterrogacao);
        for (int i = 0; i < visiveis; i++) {
            desenharInterrogacao(canvas, interrogacao, cara, INTERROGACOES[i]);
        }
    }

    private static void desenharInterrogacao(Canvas canvas, Drawable imagem, Rect cara,
                                             float[] onde) {
        float altura = onde[2] * cara.height();
        float largura = altura * imagem.getIntrinsicWidth() / imagem.getIntrinsicHeight();
        float centroX = cara.left + onde[0] * cara.width();
        float centroY = cara.top + onde[1] * cara.height();
        imagem.setBounds(Math.round(centroX - largura / 2f), Math.round(centroY - altura / 2f),
                Math.round(centroX + largura / 2f), Math.round(centroY + altura / 2f));
        imagem.draw(canvas);
    }

    private boolean encaixar(Drawable imagem) {
        float larguraLivre = getWidth() - getPaddingLeft() - getPaddingRight();
        float alturaLivre = getHeight() - getPaddingTop() - getPaddingBottom();
        if (larguraLivre <= 0 || alturaLivre <= 0) {
            return false;
        }
        float escala = Math.min(larguraLivre / imagem.getIntrinsicWidth(),
                alturaLivre / imagem.getIntrinsicHeight());
        float largura = imagem.getIntrinsicWidth() * escala;
        float altura = imagem.getIntrinsicHeight() * escala;
        float esquerda = getPaddingLeft() + (larguraLivre - largura) / 2f;
        float topo = getPaddingTop() + (alturaLivre - altura) / 2f;
        imagem.setBounds(Math.round(esquerda), Math.round(topo),
                Math.round(esquerda + largura), Math.round(topo + altura));
        return true;
    }

    @Nullable
    private AnimatedImageDrawable carregarPulo() {
        try {
            Drawable animacao = ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(getResources(), R.drawable.veninha_pulando));
            return animacao instanceof AnimatedImageDrawable
                    ? (AnimatedImageDrawable) animacao
                    : null;
        } catch (IOException e) {
            return null;
        }
    }

    @NonNull
    private Drawable imagem(@DrawableRes int id) {
        Drawable imagem = imagens.get(id);
        if (imagem == null) {
            imagem = ContextCompat.getDrawable(getContext(), id);
            imagens.put(id, imagem);
        }
        return imagem;
    }
}
