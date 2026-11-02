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

public class AvatarPerfilView extends AppCompatImageView {

    private static final float LARGURA_FORMA = 145f;
    private static final float ALTURA_FORMA = 160f;
    private static final float ESPESSURA_CONTORNO = 2f;

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

        mascara.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

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
        if (matrizFoto != null) {
            ajustarFoto();
        }
    }

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
