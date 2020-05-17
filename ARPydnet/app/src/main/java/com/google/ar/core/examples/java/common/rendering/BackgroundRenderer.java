/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.common.rendering;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import it.unibo.cvlab.pydnet.Utils;

/**
 * This class renders the AR background from camera feed. It creates and hosts the texture given to
 * ARCore to be filled with the camera image.
 */
public class BackgroundRenderer {
    private static final String TAG = BackgroundRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/screenquad.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/screenquad.frag";

    private static final int COORDS_PER_VERTEX = 2;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int FLOAT_SIZE = Float.BYTES;

    private static final float[] QUAD_COORDS =
            new float[] {
                    -1.0f, -1.0f,   //Bottom left
                    -1.0f, +1.0f,   //Top left
                    +1.0f, -1.0f,   //Bottom right
                    +1.0f, +1.0f,   //Top Right
            };

    private static final float[] QUAD_TEXTURE_COORDS =
            new float[] {
                    0.0f, 0.0f,   //Bottom left
                    0.0f, 1.0f,   //Top left
                    1.0f, 0.0f,   //Bottom right
                    1.0f, 1.0f,   //Top Right
            };

    private static final int VERTEX_COUNT = QUAD_COORDS.length / COORDS_PER_VERTEX;

    private FloatBuffer backgroundCoordsBuffer;
    private FloatBuffer backgroundTexCoordsBuffer;
    private FloatBuffer plasmaTexCoordsBuffer;

    //Riferimento al programma shader
    private int program;

    //Riferimenti ai Sampler
    private int backgroundTextureUniform;
    private int plasmaTextureUniform;
    private int inferenceTextureUniform;

    //Riferimento all'abilitatore della maschera.
    private int plasmaEnabledUniform;
    private boolean plasmaEnabled = false;
    private int scaleFactorUniform;
    private float scaleFactor;
    private int shiftFactorUniform;
    private float shiftFactor;
    private int maxDepthUniform;
    private float maxDepth;

    public void setPlasmaEnabled(boolean plasmaEnabled) {
        this.plasmaEnabled = plasmaEnabled;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public void setMaxDepth(float maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setShiftFactor(float shiftFactor) {
        this.shiftFactor = shiftFactor;
    }

    //Riferimento alla posizione dello sfondo e della relativa maschera.
    private int backgroundPositionAttribute;

    //Riferimento alle coordinate delle texture.
    private int backgroundTexCoordAttribute;

    //Riferimento alle coordinate delle texture.
    private int plasmaTexCoordAttribute;

    private int[] textures = new int[4];

    private int getInferenceTextureId(){
        return textures[0];
    }

    public int getBackgroundTextureId() {
        return textures[1];
    }

    public int getScreenshotFrameBufferTextureId(){
        return textures[2];
    }

    private int getPlasmaTextureId(){
        return textures[3];
    }


    private int[] frameBuffers = new int[1];

    private int getScreenshotFrameBuffer(){
        return frameBuffers[0];
    }

    public void onSurfaceChanged(int surfaceWidth, int surfaceHeight){
        //Devo aggiornare anche la texture associata al framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());

        int textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(textureTarget, getScreenshotFrameBufferTextureId());

        //glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 800, 600, 0, GL_RGB, GL_UNSIGNED_BYTE, NULL);
        GLES20.glTexImage2D(textureTarget, 0, GLES20.GL_RGBA, surfaceWidth, surfaceHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        ShaderUtil.checkGLError(TAG, "Framebuffer texture allocate");

        GLES20.glBindTexture(textureTarget, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
     * the OpenGL thread.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) throws IOException {
        // Generate the textures.
        GLES20.glGenTextures(textures.length, textures, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getInferenceTextureId());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPlasmaTextureId());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //Carico la texture plasma Width: 256 height: 1
        GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGB32F, Utils.PLASMA.length / 3, 1, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, FloatBuffer.wrap(Utils.PLASMA));

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di una texture aggiuntiva dove salvare il rendering
        GLES20.glGenFramebuffers(frameBuffers.length, frameBuffers, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId(), 0);

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "Framebuffer caricato correttamente");
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Framebuffer loading");

        int numVertices = 4;

        if (numVertices != VERTEX_COUNT) {
            throw new RuntimeException("Unexpected number of vertices in BackgroundRenderer.");
        }

        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());

        backgroundCoordsBuffer = bbCoords.asFloatBuffer();
        backgroundCoordsBuffer.put(QUAD_COORDS);
        backgroundCoordsBuffer.position(0);

        ByteBuffer bbTexCoordsTransformed =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder());

        backgroundTexCoordsBuffer = bbTexCoordsTransformed.asFloatBuffer();
        backgroundTexCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        backgroundTexCoordsBuffer.rewind();

        ByteBuffer maskTexCoordsTransformed =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        maskTexCoordsTransformed.order(ByteOrder.nativeOrder());

        plasmaTexCoordsBuffer = maskTexCoordsTransformed.asFloatBuffer();
        plasmaTexCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        plasmaTexCoordsBuffer.rewind();

        int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        // create empty OpenGL ES Program
        program = GLES20.glCreateProgram();

        // add the vertex shader to program
        GLES20.glAttachShader(program, vertexShader);
        // add the fragment shader to program
        GLES20.glAttachShader(program, fragmentShader);
        // creates OpenGL ES program executables
        GLES20.glLinkProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        GLES20.glUseProgram(program);

        backgroundPositionAttribute = GLES20.glGetAttribLocation(program, "a_position");
        backgroundTexCoordAttribute = GLES20.glGetAttribLocation(program, "a_backgroundTextCoord");
        plasmaTexCoordAttribute = GLES20.glGetAttribLocation(program, "a_plasmaTextCoord");

        backgroundTextureUniform = GLES20.glGetUniformLocation(program, "u_backgroundTexture");
        plasmaTextureUniform = GLES20.glGetUniformLocation(program, "u_plasmaTexture");
        inferenceTextureUniform = GLES20.glGetUniformLocation(program, "u_inferenceTexture");

        plasmaEnabledUniform = GLES20.glGetUniformLocation(program, "u_plasmaEnabled");
        scaleFactorUniform = GLES20.glGetUniformLocation(program, "u_scaleFactor");
        shiftFactorUniform = GLES20.glGetUniformLocation(program, "u_shiftFactor");
        maxDepthUniform = GLES20.glGetUniformLocation(program, "u_maxDepth");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    /**
     * Draws the AR background image. The image will be drawn such that virtual content rendered with
     * the matrices provided by {@link com.google.ar.core.Camera#getViewMatrix(float[], int)} and
     * {@link com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)} will
     * accurately follow static physical objects. This must be called <b>before</b> drawing virtual
     * content.
     *
     * @param frame The current {@code Frame} as returned by {@link Session#update()}.
     */
    public void draw(@NonNull Frame frame) {
        // If display rotation changed (also includes view size change), we need to re-query the uv
        // coordinates for the screen rect, as they may have changed as well.
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    backgroundCoordsBuffer,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    backgroundTexCoordsBuffer);

            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    backgroundCoordsBuffer,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    plasmaTexCoordsBuffer);
        }

        if (frame.getTimestamp() == 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            return;
        }

        draw();
    }

    /**
     * Draws the camera background image using the currently configured {@link
     * BackgroundRenderer#backgroundTexCoordsBuffer} image texture coordinates.
     */
    private void draw() {
        ShaderUtil.checkGLError(TAG, "BackgroundRenderer Before Draw");

        // Ensure position is rewound before use.
        backgroundTexCoordsBuffer.position(0);
        plasmaTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(program);

        GLES20.glUniform1f(scaleFactorUniform, scaleFactor);
        GLES20.glUniform1f(shiftFactorUniform, shiftFactor);
        GLES20.glUniform1f(maxDepthUniform, maxDepth);

        //Binding delle texture
        GLES20.glUniform1i(inferenceTextureUniform, 0);
        GLES20.glUniform1i(plasmaTextureUniform, 2);
        GLES20.glUniform1i(backgroundTextureUniform,  3);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getInferenceTextureId());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPlasmaTextureId());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        GLES20.glVertexAttribPointer(
                plasmaTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, plasmaTexCoordsBuffer);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glEnableVertexAttribArray(plasmaTexCoordAttribute);

        //Binding del coefficiente: Devo farlo falso per evitare colorazioni nel framebuffer.
        GLES20.glUniform1f(plasmaEnabledUniform, 0.0f);

        //Disegno nel framebuffer
        //https://stackoverflow.com/questions/4041682/android-opengl-es-framebuffer-objects-rendering-depth-buffer-to-texture
        //https://github.com/google-ar/sceneform-android-sdk/issues/225
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        //Binding del coefficiente:
        GLES20.glUniform1f(plasmaEnabledUniform, plasmaEnabled ? 1.0f : 0.0f);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glDisableVertexAttribArray(plasmaTexCoordAttribute);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

//        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRenderer After Draw");
    }

    //https://community.khronos.org/t/loading-and-reading-a-floating-point-texture/63360
    //http://www.anandmuralidhar.com/blog/android/load-read-texture/
    //https://github.com/anandmuralidhar24/FloatTextureAndroid
    //Metodo per caricare la maschera
    public void loadInference(FloatBuffer inference, Utils.Resolution resolution){
        //Anche se in precedenza ho usato GLES20, il contesto è di tipo 3.0, compatibile con i precedenti.
        inference.rewind();

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, getInferenceTextureId());

        GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R32F, resolution.getWidth(), resolution.getHeight(), 0, GLES30.GL_RED, GLES30.GL_FLOAT, inference);

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

}
