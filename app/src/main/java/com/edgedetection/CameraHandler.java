package com.edgedetection;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import androidx.annotation.NonNull;

public class CameraHandler implements GLRenderer.SurfaceTextureListener {
    private static final String TAG = "CameraHandler";
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Context context;
    private GLRenderer glRenderer;
    private boolean useRawFeed = false;  // Start with edge detection mode
    private ImageReader imageReader;
    private MainActivity mainActivity;
    private FrameProcessor frameProcessor;
    private boolean sessionClosed = false;
    private static final int PREVIEW_WIDTH = 1280;
    private static final int PREVIEW_HEIGHT = 720;
    private android.view.Surface previewSurface;
    private SurfaceTexture currentSurfaceTexture;

    public CameraHandler(Context context, GLRenderer glRenderer) {
        this.context = context;
        this.glRenderer = glRenderer;
        this.mainActivity = (MainActivity) context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.glRenderer.setSurfaceTextureListener(this);
    }

    public void startCamera() {
        startBackgroundThread();
        Log.d(TAG, "Camera startup initiated, waiting for SurfaceTexture...");
        // Camera will start when surface texture is ready via callback
    }

    @Override
    public void onSurfaceTextureReady(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "SurfaceTexture ready, opening camera...");
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            Log.d(TAG, "Using camera: " + cameraId);
            
            // Get camera characteristics to find preview size
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageReader.class);
            Size previewSize = sizes[0];
            Log.d(TAG, "Camera size: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            
            // Create ImageReader for frame capture
            imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 2);
            Log.d(TAG, "ImageReader created: " + PREVIEW_WIDTH + "x" + PREVIEW_HEIGHT);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, backgroundHandler);
            Log.d(TAG, "ImageReader listener set");
            
            // Create frame processor
            frameProcessor = new FrameProcessor(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Camera opened successfully");
                    cameraDevice = camera;
                    try {
                        createCaptureSession(surfaceTexture);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Camera access exception", e);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Camera disconnected");
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - check permissions", e);
        }
    }
    
    private void createCaptureSession(SurfaceTexture surfaceTexture) throws CameraAccessException {
        if (cameraDevice == null) return;

        currentSurfaceTexture = surfaceTexture;
        previewSurface = new android.view.Surface(surfaceTexture);
        android.view.Surface imageSurface = imageReader.getSurface();

        try {
            cameraDevice.createCaptureSession(
                java.util.Arrays.asList(previewSurface, imageSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        sessionClosed = false;
                        try {
                            startRepeatingCapture();
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error starting capture", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Capture session configuration failed");
                        sessionClosed = true;
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
            sessionClosed = true;
        }
    }

    private void startRepeatingCapture() throws CameraAccessException {
        if (captureSession == null || cameraDevice == null) return;

        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        builder.addTarget(previewSurface);
        builder.addTarget(imageReader.getSurface());
        
        captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
    }
    
    private void onImageAvailable(ImageReader reader) {
        Log.d(TAG, "onImageAvailable called, useRawFeed=" + useRawFeed);
        Image image = reader.acquireLatestImage();
        if (image == null) {
            Log.d(TAG, "Image is null, skipping frame");
            return;
        }
        
        try {
            // Process the frame based on current mode
            byte[] processedData;
            if (useRawFeed) {
                processedData = frameProcessor.getRawFrame(image);
                Log.d(TAG, "Got raw frame, size: " + (processedData != null ? processedData.length : 0));
            } else {
                processedData = frameProcessor.processEdgeDetection(image);
                Log.d(TAG, "Got edge detection frame, size: " + (processedData != null ? processedData.length : 0));
            }
            
            if (processedData != null) {
                // Update renderer with processed frame
                glRenderer.updateFrame(processedData, PREVIEW_WIDTH, PREVIEW_HEIGHT, !useRawFeed);
                Log.d(TAG, "Frame updated in renderer");
            }
        } finally {
            image.close();
        }
    }
    
    public void setProcessingMode(boolean rawFeed) {
        this.useRawFeed = rawFeed;
        String mode = rawFeed ? "RAW FEED" : "EDGE DETECTION";
        Log.d(TAG, "Processing mode changed to: " + mode + " (useRawFeed=" + rawFeed + ")");
    }

    public void stopCamera() {
        Log.d(TAG, "stopCamera called");
        
        // Mark session as closed immediately to prevent new requests
        sessionClosed = true;
        
        try {
            // Stop capture request first
            if (captureSession != null) {
                try {
                    captureSession.stopRepeating();
                    Log.d(TAG, "Stopped repeating capture");
                } catch (CameraAccessException e) {
                    Log.w(TAG, "CameraAccessException while stopping repeating: " + e.getMessage());
                } catch (IllegalStateException e) {
                    Log.w(TAG, "IllegalStateException while stopping repeating (expected if already closed): " + e.getMessage());
                } catch (Exception e) {
                    Log.w(TAG, "Unexpected exception while stopping repeating: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
            
            // Close capture session
            if (captureSession != null) {
                try {
                    captureSession.close();
                    Log.d(TAG, "Closed capture session");
                } catch (Exception e) {
                    Log.w(TAG, "Error closing session: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
                captureSession = null;
            }
            
            // Close preview surface
            if (previewSurface != null) {
                try {
                    previewSurface.release();
                    Log.d(TAG, "Released preview surface");
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing preview surface: " + e.getClass().getSimpleName());
                }
                previewSurface = null;
            }
            
            // Close ImageReader
            if (imageReader != null) {
                try {
                    imageReader.close();
                    Log.d(TAG, "Closed image reader");
                } catch (Exception e) {
                    Log.w(TAG, "Error closing image reader: " + e.getClass().getSimpleName());
                }
                imageReader = null;
            }
            
            // Close camera device
            if (cameraDevice != null) {
                try {
                    cameraDevice.close();
                    Log.d(TAG, "Closed camera device");
                } catch (Exception e) {
                    Log.w(TAG, "Error closing camera device: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
                cameraDevice = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in stopCamera: " + e.getClass().getSimpleName(), e);
        } finally {
            stopBackgroundThread();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while stopping background thread", e);
            }
        }
    }
}
