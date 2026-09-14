package com.afmvfcc.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

public class QrCodeGenerator {

    /** Renders {@code content} as a QR code image, ready to drop into an ImageView. */
    public static Image generate(String content, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            BufferedImage bufferedImage = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bufferedImage.setRGB(x, y, matrix.get(x, y) ? 0x1E2130 : 0xFFFFFF);
                }
            }
            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (WriterException e) {
            System.err.println("[QrCodeGenerator] Could not generate QR code: " + e.getMessage());
            return null;
        }
    }
}
