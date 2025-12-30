package com.feng.socketdemo.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class YuvUtils {

    private Bitmap yuvToBitmap(byte[] yuv, int format, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(yuv, ImageFormat.NV21, width, height, null);
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        return image;
    }

    /**
     * 手动转换 NV21 到 ARGB_8888
     */
    public static Bitmap nv21ToBitmapManual(byte[] nv21, int width, int height) {
        int[] argb = new int[width * height];

        final int frameSize = width * height;
        final int uvOffset = frameSize;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = uvOffset + (j >> 1) * width;
            int u = 0, v = 0;

            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & nv21[yp]) - 16;
                if (y < 0) y = 0;

                if ((i & 1) == 0) {
                    v = (0xff & nv21[uvp++]) - 128;
                    u = (0xff & nv21[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = clamp(r, 0, 262143);
                g = clamp(g, 0, 262143);
                b = clamp(b, 0, 262143);

                argb[yp] = 0xff000000 | ((r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(argb, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * 将 NV21 格式的 YUV 数据转换为 Bitmap
     *
     * @param nv21Data NV21 数据
     * @param width    图片宽度
     * @param height   图片高度
     * @return Bitmap 对象
     */
    public static Bitmap nv21ToBitmap(byte[] nv21Data, int width, int height) {
        YuvImage yuvImage = new YuvImage(nv21Data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 压缩为 JPEG
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] jpegData = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
    }

    /**
     * 从文件加载 NV21 数据并转换为 Bitmap
     *
     * @param filePath 文件路径
     * @param width    图片宽度
     * @param height   图片高度
     * @return Bitmap 对象
     */
    public static Bitmap loadNv21FromFile(String filePath, int width, int height) {
        try {
            // 读取 YUV 文件
            FileInputStream fis = new FileInputStream(filePath);
            byte[] yuvData = new byte[width * height * 3 / 2]; // NV21 大小
            fis.read(yuvData);
            fis.close();

            return nv21ToBitmap(yuvData, width, height);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
