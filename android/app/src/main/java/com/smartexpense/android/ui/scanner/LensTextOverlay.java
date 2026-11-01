package com.smartexpense.android.ui.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Draws live ML Kit text bounds over the CameraX preview using PreviewView fillCenter geometry. */
public final class LensTextOverlay extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Rect> boxes = new ArrayList<>();
    private int imageWidth;
    private int imageHeight;
    private int rotation;

    public LensTextOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        fillPaint.setColor(0x3385F5E8);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(0xFF85F5E8);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
    }

    public void setTextBoxes(List<Rect> newBoxes, int width, int height, int rotationDegrees) {
        boxes = new ArrayList<>();
        for (Rect box : newBoxes) boxes.add(new Rect(box));
        imageWidth = width;
        imageHeight = height;
        rotation = ((rotationDegrees % 360) + 360) % 360;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageWidth <= 0 || imageHeight <= 0 || boxes.isEmpty()) return;
        boolean quarterTurn = rotation == 90 || rotation == 270;
        float sourceWidth = quarterTurn ? imageHeight : imageWidth;
        float sourceHeight = quarterTurn ? imageWidth : imageHeight;
        float scale = Math.max(getWidth() / sourceWidth, getHeight() / sourceHeight);
        float offsetX = (getWidth() - sourceWidth * scale) / 2f;
        float offsetY = (getHeight() - sourceHeight * scale) / 2f;
        for (Rect box : boxes) {
            float l, t, r, b;
            switch (rotation) {
                case 90:  l = imageHeight - box.bottom; t = box.left; r = imageHeight - box.top; b = box.right; break;
                case 180: l = imageWidth - box.right; t = imageHeight - box.bottom; r = imageWidth - box.left; b = imageHeight - box.top; break;
                case 270: l = box.top; t = imageWidth - box.right; r = box.bottom; b = imageWidth - box.left; break;
                default:  l = box.left; t = box.top; r = box.right; b = box.bottom;
            }
            RectF mapped = new RectF(offsetX + l * scale, offsetY + t * scale,
                    offsetX + r * scale, offsetY + b * scale);
            canvas.drawRoundRect(mapped, 5f, 5f, fillPaint);
            canvas.drawRoundRect(mapped, 5f, 5f, strokePaint);
        }
    }
}
