package com.edgedetection;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRenderer";
    private int glProgram;
    private int positionHandle;
    private int texCoordHandle;
    private int textureHandle;
    private int frameTexture;
    private SurfaceTexture surfaceTexture;
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private long frameCount = 0;
    private long lastTime = 0;
    private int fps = 0;

    private static final String VERTEX_SHADER =
        "attribute vec4 position;\n" +
        "attribute vec2 inputTexCoord;\n" +
        "varying vec2 texCoord;\n" +
        "void main() {\n" +
        "  gl_Position = position;\n" +
        "  texCoord = inputTexCoord;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
        "uniform sampler2D texture;\n" +
        "varying vec2 texCoord;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(texture, texCoord);\n" +
        "}\n";

    public GLRenderer() {
        // Initialize vertex data
        float[] vertices = {
            -1.0f,  1.0f,
            -1.0f, -1.0f,
             1.0f,  1.0f,
             1.0f, -1.0f
        };

        float[] texCoords = {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        };

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(texCoords).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        frameTexture = createTextureObject();
        surfaceTexture = new SurfaceTexture(frameTexture);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(glProgram);

        positionHandle = GLES20.glGetAttribLocation(glProgram, "position");
        texCoordHandle = GLES20.glGetAttribLocation(glProgram, "inputTexCoord");
        textureHandle = GLES20.glGetUniformLocation(glProgram, "texture");

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(texCoordHandle);

        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

        texCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTexture);
        GLES20.glUniform1i(textureHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);

        updateFPS();
    }

    private int createProgram(String vertexShaderCode, String fragmentShaderCode) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        return program;
    }

    private int compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private int createTextureObject() {
        int[] textureHandle = new int[1];
        GLES20.glGenTextures(1, textureHandle, 0);
        int textureId = textureHandle[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        return textureId;
    }

    private void updateFPS() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= 1000) {
            fps = (int) frameCount;
            frameCount = 0;
            lastTime = currentTime;
            Log.d(TAG, "FPS: " + fps);
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public int getFPS() {
        return fps;
    }
}
