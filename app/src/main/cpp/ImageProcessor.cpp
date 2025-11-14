#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cmath>

#define LOG_TAG "EdgeDetection"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Simple edge detection using Sobel operator
extern "C" {

JNIEXPORT void JNICALL
Java_com_edgedetection_MainActivity_processFrame(
        JNIEnv *env,
        jobject thiz,
        jbyteArray inputFrame,
        jint width,
        jint height,
        jbyteArray outputFrame,
        jint mode) {

    jbyte *inputData = env->GetByteArrayElements(inputFrame, nullptr);
    jbyte *outputData = env->GetByteArrayElements(outputFrame, nullptr);

    try {
        // NV21 format: Y plane followed by interleaved UV
        int ySize = width * height;
        
        if (mode == 1) {
            // Edge detection mode - process Y plane only
            unsigned char *yData = (unsigned char *)inputData;
            unsigned char *output = (unsigned char *)outputData;
            
            // Copy Y plane
            memcpy(output, inputData, ySize + ySize / 2);
            
            // Apply simple edge detection on Y plane
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int idx = y * width + x;
                    
                    // Sobel operator
                    int gx = (-1 * yData[(y-1)*width + (x-1)]) + (1 * yData[(y-1)*width + (x+1)]) +
                             (-2 * yData[y*width + (x-1)]) + (2 * yData[y*width + (x+1)]) +
                             (-1 * yData[(y+1)*width + (x-1)]) + (1 * yData[(y+1)*width + (x+1)]);
                    
                    int gy = (-1 * yData[(y-1)*width + (x-1)]) + (-2 * yData[(y-1)*width + x]) + (-1 * yData[(y-1)*width + (x+1)]) +
                             (1 * yData[(y+1)*width + (x-1)]) + (2 * yData[(y+1)*width + x]) + (1 * yData[(y+1)*width + (x+1)]);
                    
                    int magnitude = (int)sqrt(gx*gx + gy*gy);
                    output[idx] = magnitude > 128 ? 255 : 0;
                }
            }
        } else {
            // Normal mode - pass through
            memcpy(outputData, inputData, ySize + ySize / 2);
        }
        
        LOGD("Frame processed: %dx%d mode=%d", width, height, mode);
        
    } catch (...) {
        LOGD("Error processing frame");
    }

    env->ReleaseByteArrayElements(inputFrame, inputData, JNI_ABORT);
    env->ReleaseByteArrayElements(outputFrame, outputData, 0);
}

}
