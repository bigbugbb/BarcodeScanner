package com.bigbug.barcodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Runnable that saves an {@link Image} into the specified {@link File}, and updates
 * {@link android.provider.MediaStore} to include the resulting file.
 * <p/>
 * This can be constructed through an {@link BarcodeDetecterBuilder} as the necessary image and
 * result information becomes available.
 */
public class BarcodeDetecter implements Runnable {

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeDetecter";

    /**
     * The image to save.
     */
    private final Image mImage;

    /**
     * The CaptureResult for this image capture.
     */
    private final CaptureResult mCaptureResult;

    /**
     * The CameraCharacteristics for this camera device.
     */
    private final CameraCharacteristics mCharacteristics;

    /**
     * The Context to use when updating MediaStore with the saved images.
     */
    private final Context mContext;

    /**
     * A reference counted wrapper for the ImageReader that owns the given image.
     */
    private final RefCountedAutoCloseable<ImageReader> mReader;

    private WeakReference<OnBarcodeDetectedListener> mListenerRef;

    private BarcodeDetecter(Image image, CaptureResult result,
                            CameraCharacteristics characteristics, Context context,
                            RefCountedAutoCloseable<ImageReader> reader) {
        mImage = image;
        mCaptureResult = result;
        mCharacteristics = characteristics;
        mContext = context;
        mReader = reader;
    }

    @Override
    public void run() {
        int format = mImage.getFormat();
        switch (format) {
            case ImageFormat.JPEG: {
                final ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                try {
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    int[] rgb = new int[bm.getWidth() * bm.getHeight()];
                    // copy pixel data from the Bitmap into the 'intArray' array
                    bm.getPixels(rgb, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
                    LuminanceSource source = new RGBLuminanceSource(bm.getWidth(), bm.getHeight(), rgb);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    Result result = new MultiFormatReader().decode(bitmap, null);
                    if (result != null) {
                        final String content = result.getText();
                        Application.getInstance().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                for (OnBarcodeDetectedListener barcodeDetectedListener :
                                        Application.getInstance().getUIListeners(OnBarcodeDetectedListener.class)) {
                                    barcodeDetectedListener.onBarcodeDetected(content);
                                }
                            }
                        });
                    }
                } catch (NotFoundException e) {
                    e.printStackTrace();
                } finally {
                    mImage.close();
                }
                break;
            }
            default: {
                Log.e(TAG, "Cannot detect barcode, unexpected image format:" + format);
                break;
            }
        }

        // Decrement reference count to allow ImageReader to be closed to free up resources.
        mReader.close();
    }

    /**
     * Builder class for constructing {@link BarcodeDetecter}s.
     * <p/>
     * This class is thread safe.
     */
    public static class BarcodeDetecterBuilder {
        private Image mImage;
        private CaptureResult mCaptureResult;
        private CameraCharacteristics mCharacteristics;
        private Context mContext;
        private RefCountedAutoCloseable<ImageReader> mReader;

        /**
         * Construct a new BarcodeDetecterBuilder using the given {@link Context}.
         *
         * @param context a {@link Context} to for accessing the
         *                {@link android.provider.MediaStore}.
         */
        public BarcodeDetecterBuilder(final Context context) {
            mContext = context;
        }

        public synchronized BarcodeDetecterBuilder setRefCountedReader(RefCountedAutoCloseable<ImageReader> reader) {
            if (reader == null) throw new NullPointerException();

            mReader = reader;
            return this;
        }

        public synchronized BarcodeDetecterBuilder setImage(final Image image) {
            if (image == null) throw new NullPointerException();
            mImage = image;
            return this;
        }

        public synchronized BarcodeDetecterBuilder setResult(final CaptureResult result) {
            if (result == null) throw new NullPointerException();
            mCaptureResult = result;
            return this;
        }

        public synchronized BarcodeDetecterBuilder setCharacteristics(final CameraCharacteristics characteristics) {
            if (characteristics == null) throw new NullPointerException();
            mCharacteristics = characteristics;
            return this;
        }

        public synchronized BarcodeDetecter buildIfComplete() {
            if (!isComplete()) {
                return null;
            }
            return new BarcodeDetecter(mImage, mCaptureResult, mCharacteristics, mContext, mReader);
        }

        private boolean isComplete() {
            return mImage != null && mCaptureResult != null && mCharacteristics != null;
        }
    }
}