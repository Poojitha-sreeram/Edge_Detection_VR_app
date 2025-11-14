#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <android/log.h>
#include <vector>

#define LOG_TAG "EdgeDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

// Global storage for processed frame
static Mat processedFrame;
static int lastWidth = 0;
static int lastHeight = 0;

// Apply Canny edge detection
Mat applyEdgeDetection(const Mat& inputFrame) {
    Mat gray, edges;
    
    // Convert to grayscale if needed
    if (inputFrame.channels() == 4) {
        cvtColor(inputFrame, gray, COLOR_RGBA2GRAY);
    } else if (inputFrame.channels() == 3) {
        cvtColor(inputFrame, gray, COLOR_RGB2GRAY);
    } else {
        gray = inputFrame.clone();
    }
    
    // Apply Gaussian blur for noise reduction
    GaussianBlur(gray, gray, Size(5, 5), 1.0);
    
    // Apply Canny edge detection
    Canny(gray, edges, 50, 150);
    
    return edges;
}

// Apply grayscale filter
Mat applyGrayscale(const Mat& inputFrame) {
    Mat gray;
    if (inputFrame.channels() == 4) {
        cvtColor(inputFrame, gray, COLOR_RGBA2GRAY);
    } else if (inputFrame.channels() == 3) {
        cvtColor(inputFrame, gray, COLOR_RGB2GRAY);
    } else {
        gray = inputFrame.clone();
    }
    return gray;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_edgedetection_MainActivity_processFrame(
        JNIEnv *env,
        jobject thiz,
        jbyteArray frameData,
        jint width,
        jint height) {
    
    try {
        jbyte *data = env->GetByteArrayElements(frameData, nullptr);
        int size = env->GetArrayLength(frameData);
        
        // Create Mat from byte array (NV21 format typical for Android camera)
        Mat yuv(height + height / 2, width, CV_8UC1, (uchar *) data);
        Mat rgb;
        
        // Convert NV21 to RGB
        cvtColor(yuv, rgb, COLOR_YUV2RGB_NV21);
        
        // Store dimensions
        lastWidth = width;
        lastHeight = height;
        
        // Apply edge detection
        processedFrame = applyEdgeDetection(rgb);
        
        LOGI("Frame processed: %d x %d", width, height);
        
        env->ReleaseByteArrayElements(frameData, data, 0);
        
    } catch (Exception &e) {
        LOGE("Exception: %s", e.what());
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_edgedetection_MainActivity_getProcessedFrame(
        JNIEnv *env,
        jobject thiz) {
    
    try {
        if (processedFrame.empty()) {
            return nullptr;
        }
        
        // Convert Mat to byte array
        int size = processedFrame.total() * processedFrame.channels();
        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, (jbyte *) processedFrame.data);
        
        return result;
        
    } catch (Exception &e) {
        LOGE("Exception: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_edgedetection_MainActivity_getFrameDimensions(
        JNIEnv *env,
        jobject thiz,
        jintArray dimensions) {
    
    jint dims[2] = {lastWidth, lastHeight};
    env->SetIntArrayRegion(dimensions, 0, 2, dims);
}

// Apply invert effect using GLSL-like operation in OpenCV
JNIEXPORT jbyteArray JNICALL
Java_com_edgedetection_ImageProcessor_applyInvertEffect(
        JNIEnv *env,
        jobject thiz,
        jbyteArray frameData,
        jint width,
        jint height) {
    
    try {
        jbyte *data = env->GetByteArrayElements(frameData, nullptr);
        Mat frame(height, width, CV_8UC3, (uchar *) data);
        
        Mat inverted;
        bitwise_not(frame, inverted);
        
        jbyteArray result = env->NewByteArray(width * height * 3);
        env->SetByteArrayRegion(result, 0, width * height * 3, (jbyte *) inverted.data);
        env->ReleaseByteArrayElements(frameData, data, 0);
        
        return result;
        
    } catch (Exception &e) {
        LOGE("Exception: %s", e.what());
        return nullptr;
    }
}

}
