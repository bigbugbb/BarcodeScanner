package com.bigbug.barcodescanner;


public interface OnBarcodeDetectedListener extends BaseUIListener {
    void onBarcodeDetected(String content);
}