package com.venussystem.venusmobile.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Guia visual da câmera, alinhada com a foto vertical produzida pelo CameraX. */
public class ScanCameraOverlayView extends View {

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();
    private float density;

    public ScanCameraOverlayView(Context context) {
        super(context);
        init();
    }

    public ScanCameraOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScanCameraOverlayView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(3));
        framePaint.setColor(0xFFFFFFFF);
        framePaint.setStrokeCap(Paint.Cap.ROUND);
        framePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenWidth = getWidth();
        final float screenHeight = getHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        final float horizontalMargin = dp(28);
        final float topReserved = dp(132);
        final float bottomReserved = dp(104);

        final float maxWidth = screenWidth - (horizontalMargin * 2f);
        final float usableHeight = Math.max(
                dp(240),
                screenHeight - topReserved - bottomReserved
        );

        float frameWidth = Math.min(
                maxWidth,
                usableHeight * 9f / 16f
        );
        float frameHeight = frameWidth * 16f / 9f;

        final float centerY =
                (topReserved + (screenHeight - bottomReserved)) / 2f;

        final float left = (screenWidth - frameWidth) / 2f;
        final float top = centerY - frameHeight / 2f;
        final float right = left + frameWidth;
        final float bottom = top + frameHeight;

        frameRect.set(left, top, right, bottom);
        canvas.drawRoundRect(
                frameRect,
                dp(18),
                dp(18),
                framePaint
        );
    }

    private float dp(float value) {
        return value * density;
    }
}
