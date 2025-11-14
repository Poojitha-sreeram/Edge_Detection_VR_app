package com.edgedetection;

import android.media.Image;
import android.util.Log;
import java.nio.ByteBuffer;

public class FrameProcessor {
    private static final String TAG = "FrameProcessor";
    private byte[] yData;
    private byte[] uData;
    private byte[] vData;
    private byte[] outputData;
    private int width;
    private int height;

    public FrameProcessor(int width, int height) {
        // Limit to reasonable size for processing
        this.width = Math.min(width, 1280);
        this.height = Math.min(height, 720);
        
        int expectedSize = this.width * this.height;
        this.yData = new byte[expectedSize];
        this.uData = new byte[expectedSize / 4];
        this.vData = new byte[expectedSize / 4];
        this.outputData = new byte[expectedSize * 3 / 2]; // NV21 format
        
        Log.d(TAG, "FrameProcessor initialized: " + this.width + "x" + this.height);
    }

    public byte[] processEdgeDetection(Image image) {
        // Extract YUV planes from image
        Image.Plane[] planes = image.getPlanes();
        
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        
        int ySize = Math.min(yBuffer.remaining(), yData.length);
        int uSize = Math.min(uBuffer.remaining(), uData.length);
        int vSize = Math.min(vBuffer.remaining(), vData.length);
        
        Log.d(TAG, "Buffer sizes - Y: " + ySize + "/" + yData.length + 
              ", U: " + uSize + "/" + uData.length + 
              ", V: " + vSize + "/" + vData.length);
        
        yBuffer.get(yData, 0, ySize);
        uBuffer.get(uData, 0, uSize);
        vBuffer.get(vData, 0, vSize);
        
        // Apply Sobel edge detection on Y plane (modifies yData and copies to outputData)
        applySobelEdgeDetection();
        
        return outputData;
    }

    private void applySobelEdgeDetection() {
        try {
            // Output: black edges on white background
            int edgeCount = 0;
            int totalPixels = 0;
            int maxMagnitude = 0;
            int minMagnitude = Integer.MAX_VALUE;
            long sumMagnitude = 0;
            
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int idx = y * width + x;
                    
                    // Bounds check
                    if (idx >= yData.length) {
                        Log.e(TAG, "Index out of bounds in edge detection: " + idx + " >= " + yData.length);
                        return;
                    }
                    
                    // Sobel operator for X direction
                    int gx = (-1 * (yData[(y-1)*width + (x-1)] & 0xFF)) + (0 * (yData[(y-1)*width + x] & 0xFF)) + (1 * (yData[(y-1)*width + (x+1)] & 0xFF)) +
                             (-2 * (yData[y*width + (x-1)] & 0xFF)) + (0 * (yData[y*width + x] & 0xFF)) + (2 * (yData[y*width + (x+1)] & 0xFF)) +
                             (-1 * (yData[(y+1)*width + (x-1)] & 0xFF)) + (0 * (yData[(y+1)*width + x] & 0xFF)) + (1 * (yData[(y+1)*width + (x+1)] & 0xFF));
                    
                    // Sobel operator for Y direction
                    int gy = (-1 * (yData[(y-1)*width + (x-1)] & 0xFF)) + (-2 * (yData[(y-1)*width + x] & 0xFF)) + (-1 * (yData[(y-1)*width + (x+1)] & 0xFF)) +
                             (0 * (yData[y*width + (x-1)] & 0xFF)) + (0 * (yData[y*width + x] & 0xFF)) + (0 * (yData[y*width + (x+1)] & 0xFF)) +
                             (1 * (yData[(y+1)*width + (x-1)] & 0xFF)) + (2 * (yData[(y+1)*width + x] & 0xFF)) + (1 * (yData[(y+1)*width + (x+1)] & 0xFF));
                    
                    // Calculate magnitude
                    int magnitude = (int) Math.sqrt(gx*gx + gy*gy);
                    
                    // Track magnitude statistics
                    maxMagnitude = Math.max(maxMagnitude, magnitude);
                    minMagnitude = Math.min(minMagnitude, magnitude);
                    sumMagnitude += magnitude;
                    
                    // Black edges (0) on white background (255) - threshold based on observed avg ~600
                    if (magnitude > 750) {
                        yData[idx] = (byte)0;
                        edgeCount++;
                    } else {
                        yData[idx] = (byte)255;
                    }
                    totalPixels++;
                }
            }
            
            // Log detailed edge detection statistics
            float edgePercent = (edgeCount * 100.0f) / totalPixels;
            float avgMagnitude = sumMagnitude / (float)totalPixels;
            Log.d(TAG, "Edge Stats - Edges: " + edgeCount + "/" + totalPixels + " (" + String.format("%.2f", edgePercent) + "%)");
            Log.d(TAG, "Magnitude - Min: " + minMagnitude + ", Max: " + maxMagnitude + ", Avg: " + String.format("%.2f", avgMagnitude));
            Log.d(TAG, "Threshold: 750 (edges detected above this value)");
            
            // Copy edge-detected Y plane to output
            System.arraycopy(yData, 0, outputData, 0, yData.length);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in applySobelEdgeDetection: " + e.getMessage(), e);
        }
    }

    public byte[] getRawFrame(Image image) {
        try {
            // Extract YUV planes and convert to NV21 format
            Image.Plane[] planes = image.getPlanes();
            
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            
            int ySize = Math.min(yBuffer.remaining(), outputData.length);
            yBuffer.get(outputData, 0, ySize);
            
            int uvStartIdx = width * height;
            int uvPixelStride = planes[1].getPixelStride();
            
            if (uvPixelStride == 1) {
                // Packed format
                int uvSize = Math.min(uBuffer.remaining(), outputData.length - uvStartIdx);
                uBuffer.get(outputData, uvStartIdx, uvSize);
            } else {
                // Semi-planar format
                int uvBufferSize = uBuffer.remaining();
                byte[] uvPixels = new byte[uvBufferSize];
                uBuffer.get(uvPixels);
                
                // Interleave U and V
                int pos = uvStartIdx;
                int maxPos = outputData.length;
                for (int i = 0; i < uvPixels.length / 2 && pos < maxPos - 1; i++) {
                    outputData[pos++] = uvPixels[i * 2 + 1]; // V
                    outputData[pos++] = uvPixels[i * 2];     // U
                }
            }
            
            return outputData;
        } catch (Exception e) {
            Log.e(TAG, "Error in getRawFrame: " + e.getMessage(), e);
            return outputData;
        }
    }
}
