package com.venussystem.venusmobile.view.componente;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.PathParser;

import com.venussystem.venusmobile.R;

/**
 * A foto de perfil num circulo, por cima do escudo com ondas do design.
 *
 * O escudo lilas com contorno fica atras, e a foto (se houver) entra num
 * circulo centralizado nele, ocupando 80% da largura do escudo - a borda lilas
 * aparece em volta, como no design. Sem foto, fica so o escudo. A foto
 * preenche o circulo inteiro, cortando o que sobrar, como num centerCrop.
 *
 * O formato vem de R.string.caminho_forma_avatar, num quadro de 145x160, e e
 * centralizado no tamanho da view sem distorcer.
 */
public class AvatarPerfilView extends AppCompatImageView {

    private static final float LARGURA_FORMA = 145f;
    private static final float ALTURA_FORMA = 160f;
    private static final float ESPESSURA_CONTORNO = 2f;

    // Medido no design: o circulo da foto tem 80% da largura do escudo e o
    // centro no centro do escudo.
    private static final float PROPORCAO_FOTO = 0.8f;

    private final Path formaOriginal;
    private final Path forma = new Path();
    private final Matrix ajuste = new Matrix();
    private final RectF circulo = new RectF();
    private final Path foraDoCirculo = new Path();
    private final Matrix matrizFoto = new Matrix();
    private final Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint contorno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mascara = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AvatarPerfilView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarPerfilView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        formaOriginal = PathParser.createPathFromPathData(
                context.getString(R.string.caminho_forma_avatar));

        fundo.setStyle(Paint.Style.FILL);
        fundo.setColor(ContextCompat.getColor(context, R.color.lilas_avatar));

        contorno.setStyle(Paint.Style.STROKE);
        contorno.setStrokeJoin(Paint.Join.ROUND);
        contorno.setColor(ContextCompat.getColor(context, R.color.roxo_avatar_borda));

        // Apaga a foto fora do circulo, com a borda suavizada (um clipPath
        // deixaria a borda serrilhada). Tem que ser o "fora" pintado com
        // DST_OUT: um DST_IN pintando o circulo so mexeria nos pixels do
        // proprio circulo, e os cantos da foto ficariam.
        mascara.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        // A posicao da foto e calculada aqui (ver ajustarFoto), e nao pelo
        // centerCrop, porque ela preenche o circulo e nao a view inteira.
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onSizeChanged(int largura, int altura, int larguraAntiga, int alturaAntiga) {
        super.onSizeChanged(largura, altura, larguraAntiga, alturaAntiga);

        float escala = Math.min(largura / LARGURA_FORMA, altura / ALTURA_FORMA);
        float sobraX = (largura - LARGURA_FORMA * escala) / 2f;
        float sobraY = (altura - ALTURA_FORMA * escala) / 2f;

        ajuste.setScale(escala, escala);
        ajuste.postTranslate(sobraX, sobraY);
        formaOriginal.transform(ajuste, forma);
        contorno.setStrokeWidth(ESPESSURA_CONTORNO * escala);

        float raio = PROPORCAO_FOTO * LARGURA_FORMA * escala / 2f;
        float centroX = sobraX + LARGURA_FORMA * escala / 2f;
        float centroY = sobraY + ALTURA_FORMA * escala / 2f;
        circulo.set(centroX - raio, centroY - raio, centroX + raio, centroY + raio);

        foraDoCirculo.reset();
        foraDoCirculo.addOval(circulo, Path.Direction.CW);
        foraDoCirculo.setFillType(Path.FillType.INVERSE_EVEN_ODD);

        ajustarFoto();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        // O construtor da ImageView ja chama isto, antes dos campos daqui existirem.
        if (matrizFoto != null) {
            ajustarFoto();
        }
    }

    /** Escala a foto para cobrir o circulo inteiro, centralizada nele. */
    private void ajustarFoto() {
        Drawable foto = getDrawable();
        if (foto == null || circulo.isEmpty()) {
            return;
        }
        int largura = foto.getIntrinsicWidth();
        int altura = foto.getIntrinsicHeight();
        if (largura <= 0 || altura <= 0) {
            return;
        }
        float escala = Math.max(circulo.width() / largura, circulo.height() / altura);
        matrizFoto.setScale(escala, escala);
        matrizFoto.postTranslate(circulo.centerX() - largura * escala / 2f,
                circulo.centerY() - altura * escala / 2f);
        setImageMatrix(matrizFoto);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawPath(forma, fundo);
        canvas.drawPath(forma, contorno);

        if (getDrawable() != null && !circulo.isEmpty()) {
            int camada = canvas.saveLayer(circulo, null);
            super.onDraw(canvas);
            canvas.drawPath(foraDoCirculo, mascara);
            canvas.restoreToCount(camada);
        }
    }
}
