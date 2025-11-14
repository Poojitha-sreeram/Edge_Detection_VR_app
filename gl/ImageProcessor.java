package com.edgedetection;

public class ImageProcessor {
    public native byte[] applyInvertEffect(byte[] frameData, int width, int height);
}
