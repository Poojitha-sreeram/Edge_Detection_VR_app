package com.edgedetection;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class OpenGLSurfaceView extends GLSurfaceView {
    private GLRenderer renderer;

    public OpenGLSurfaceView(Context context) {
        super(context);
        
        setEGLContextClientVersion(2);
        renderer = new GLRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public GLRenderer getRenderer() {
        return renderer;
    }
}
