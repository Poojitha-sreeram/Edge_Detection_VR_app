package com.edgedetection;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRenderer";
    
    private int program;
    private int processedProgram;
    private int positionHandle;
    private int texCoordHandle;
    private int textureHandle;
    private int processedPositionHandle;
    private int processedTexCoordHandle;
    private int processedTextureHandle;
    private int textureId;
    private SurfaceTexture surfaceTexture;
    private float[] mSTMatrix = new float[16];
    
    private long frameCount = 0;
    private long lastTime = 0;
    private float fps = 0.0f;
    
    private SurfaceTextureListener surfaceTextureListener;
    private MainActivity mainActivity;
    
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    
    private byte[] frameData;
    private boolean hasNewFrame = false;
    private boolean isProcessedFrame = false;
    private int processedWidth = 0;
    private int processedHeight = 0;
    private int processedTextureId = -1;
    
    public interface SurfaceTextureListener {
        void onSurfaceTextureReady(SurfaceTexture surfaceTexture);
    }
    
    public void setSurfaceTextureListener(SurfaceTextureListener listener) {
        this.surfaceTextureListener = listener;
    }
    
    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        
        // Compile shaders for camera (OES external texture)
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, getVertexShaderCode());
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShaderCode());
        
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        
        // Check link status
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Failed to link program: " + GLES20.glGetProgramInfoLog(program));
        }
        
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord");
        textureHandle = GLES20.glGetUniformLocation(program, "sTexture");
        
        // Compile shaders for processed frames (regular 2D texture)
        int processedFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, getProcessedFragmentShaderCode());
        
        processedProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(processedProgram, vertexShader);
        GLES20.glAttachShader(processedProgram, processedFragmentShader);
        GLES20.glLinkProgram(processedProgram);
        
        // Check link status
        GLES20.glGetProgramiv(processedProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Failed to link processed program: " + GLES20.glGetProgramInfoLog(processedProgram));
        }
        
        processedPositionHandle = GLES20.glGetAttribLocation(processedProgram, "vPosition");
        processedTexCoordHandle = GLES20.glGetAttribLocation(processedProgram, "vTexCoord");
        processedTextureHandle = GLES20.glGetUniformLocation(processedProgram, "sTexture");
        
        // Create texture using OES_EGL_image_external
        int[] textureArray = new int[1];
        GLES20.glGenTextures(1, textureArray, 0);
        textureId = textureArray[0];
        
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        
        // Create vertex and texture coordinate buffers
        float[] vertices = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        };
        
        float[] texCoords = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        };
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);
        
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(texCoords).position(0);
        
        // Create SurfaceTexture
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(st -> {
            Log.d(TAG, "Frame available from camera");
        });
        
        if (surfaceTextureListener != null) {
            surfaceTextureListener.onSurfaceTextureReady(surfaceTexture);
        }
        
        Log.d(TAG, "OpenGL surface created with texture ID: " + textureId);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        
        // Update texture based on mode
        if (isProcessedFrame && hasNewFrame && frameData != null) {
            // Use processed frame as regular 2D texture
            if (processedTextureId == -1) {
                int[] texArray = new int[1];
                GLES20.glGenTextures(1, texArray, 0);
                processedTextureId = texArray[0];
            }
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            
            // Upload grayscale edge detection data (only Y plane)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
                processedWidth, processedHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, 
                ByteBuffer.wrap(frameData, 0, processedWidth * processedHeight));
            
            hasNewFrame = false;
        } else if (!isProcessedFrame && surfaceTexture != null) {
            // Use camera texture (OES external)
            surfaceTexture.updateTexImage();
        }
        
        // Use appropriate shader program
        if (isProcessedFrame) {
            GLES20.glUseProgram(processedProgram);
            
            // Set position
            vertexBuffer.position(0);
            GLES20.glVertexAttribPointer(processedPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glEnableVertexAttribArray(processedPositionHandle);
            
            // Set texture coordinates
            texCoordBuffer.position(0);
            GLES20.glVertexAttribPointer(processedTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);
            GLES20.glEnableVertexAttribArray(processedTexCoordHandle);
            
            // Bind 2D texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId);
            GLES20.glUniform1i(processedTextureHandle, 0);
        } else {
            GLES20.glUseProgram(program);
            
            // Set position
            vertexBuffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            
            // Set texture coordinates
            texCoordBuffer.position(0);
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);
            GLES20.glEnableVertexAttribArray(texCoordHandle);
            
            // Bind OES external texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(textureHandle, 0);
        }
        
        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        
        updateFPS();
    }
    
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        
        // Check compilation status
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Failed to compile shader: " + GLES20.glGetShaderInfoLog(shader));
        }
        
        return shader;
    }
    
    private String getVertexShaderCode() {
        return "attribute vec2 vPosition;" +
               "attribute vec2 vTexCoord;" +
               "varying vec2 outTexCoord;" +
               "void main() {" +
               "  gl_Position = vec4(vPosition, 0.0, 1.0);" +
               "  outTexCoord = vTexCoord;" +
               "}";
    }
    
    private String getFragmentShaderCode() {
        return "#extension GL_OES_EGL_image_external : require\n" +
               "precision mediump float;" +
               "varying vec2 outTexCoord;" +
               "uniform samplerExternalOES sTexture;" +
               "void main() {" +
               "  gl_FragColor = texture2D(sTexture, outTexCoord);" +
               "}";
    }
    
    private String getProcessedFragmentShaderCode() {
        return "precision mediump float;" +
               "varying vec2 outTexCoord;" +
               "uniform sampler2D sTexture;" +
               "void main() {" +
               "  float gray = texture2D(sTexture, outTexCoord).r;" +
               "  gl_FragColor = vec4(vec3(gray), 1.0);" +
               "}";
    }
    
    public void updateFrame(byte[] data, int width, int height, boolean isProcessed) {
        this.frameData = data;
        this.processedWidth = width;
        this.processedHeight = height;
        this.isProcessedFrame = isProcessed;
        this.hasNewFrame = true;
        Log.d(TAG, "Frame updated: " + width + "x" + height + " processed=" + isProcessed);
    }
    
    private void updateFPS() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        if (lastTime == 0) {
            lastTime = currentTime;
        }
        if (currentTime - lastTime >= 1000) {
            fps = frameCount * 1000.0f / (currentTime - lastTime);
            frameCount = 0;
            lastTime = currentTime;
            Log.d(TAG, "FPS: " + fps);
            
            // Notify MainActivity to update UI
            if (mainActivity != null) {
                mainActivity.updateFPS((int) fps);
            }
        }
    }
    
    public float getFPS() {
        return fps;
    }
    
    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }
}
