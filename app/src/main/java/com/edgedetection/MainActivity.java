package com.edgedetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.view.SurfaceView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private CameraHandler cameraHandler;
    private GLRenderer glRenderer;
    private TextView fpsCounter;
    private Button toggleButton;
    private boolean showRawFeed = false;  // Start with edge detection mode

    static {
        System.loadLibrary("edgedetector");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // GLSurfaceView for rendering
        OpenGLSurfaceView glView = new OpenGLSurfaceView(this);
        glRenderer = glView.getRenderer();
        glRenderer.setMainActivity(this);  // Pass reference for FPS updates
        layout.addView(glView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT, 
            1.0f
        ));

        // Control panel
        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.HORIZONTAL);
        controlPanel.setPadding(10, 10, 10, 10);

        // FPS Counter
        fpsCounter = new TextView(this);
        fpsCounter.setText("FPS: 0");
        fpsCounter.setTextSize(14);
        controlPanel.addView(fpsCounter, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ));

        // Toggle Button
        toggleButton = new Button(this);
        toggleButton.setText("Raw Feed");
        toggleButton.setOnClickListener(v -> toggleProcessingMode());
        controlPanel.addView(toggleButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        layout.addView(controlPanel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(layout);

        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            initializeCamera();
        }
    }

    private void initializeCamera() {
        cameraHandler = new CameraHandler(this, glRenderer);
        cameraHandler.startCamera();
    }

    private void toggleProcessingMode() {
        showRawFeed = !showRawFeed;
        toggleButton.setText(showRawFeed ? "Edge Detection" : "Raw Feed");
        android.util.Log.d("MainActivity", "Toggle clicked: showRawFeed = " + showRawFeed);
        if (cameraHandler != null) {
            cameraHandler.setProcessingMode(showRawFeed);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraHandler != null) {
            cameraHandler.stopCamera();
        }
    }

    public void updateFPS(int fps) {
        runOnUiThread(() -> fpsCounter.setText("FPS: " + fps));
    }

    public native void processFrame(byte[] frameData, int width, int height);
}
