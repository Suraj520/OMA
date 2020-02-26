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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import it.unibo.cvlab.pydnet.Model;

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
    private FloatBuffer maskTexCoordsBuffer;

    //Riferimento al programma shader
    private int program;

    //Riferimenti ai Sampler
    private int backgroundTextureUniform;
    private int depthColorTextureUniform;

    //Riferimento all'abilitatore della maschera.
    private int depthColorEnabledUniform;

    //Riferimento alla posizione dello sfondo e della relativa maschera.
    private int backgroundPositionAttribute;

    //Riferimento alle coordinate delle texture.
    private int backgroundTexCoordAttribute;

    //Riferimento alle coordinate delle texture.
    private int maskTexCoordAttribute;

    private int[] textures = new int[3];

    public int getDepthColorTextureId(){
        return textures[0];
    }

    public int getBackgroundTextureId() {
        return textures[1];
    }

    public int getScreenshotFrameBufferTextureId(){
        return textures[2];
    }

    private boolean depthColorEnabled = false;

    public boolean isDepthColorEnabled() {
        return depthColorEnabled;
    }

    public void setDepthColorEnabled(boolean depthColorEnabled) {
        this.depthColorEnabled = depthColorEnabled;
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

        int textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(textureTarget, getDepthColorTextureId());

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(textureTarget, 0);


        textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(textureTarget, getBackgroundTextureId());

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di una texture aggiuntiva dove salvare il rendering
        GLES20.glGenFramebuffers(frameBuffers.length, frameBuffers, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());

        textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(textureTarget, getScreenshotFrameBufferTextureId());

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, textureTarget, getScreenshotFrameBufferTextureId(), 0);

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "Framebuffer caricato correttamente");
        }

        GLES20.glBindTexture(textureTarget, 0);
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

        maskTexCoordsBuffer = maskTexCoordsTransformed.asFloatBuffer();
        maskTexCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        maskTexCoordsBuffer.rewind();


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
        maskTexCoordAttribute = GLES20.glGetAttribLocation(program, "a_maskTextCoord");

        depthColorEnabledUniform = GLES20.glGetUniformLocation(program, "u_depthColorEnabled");
        backgroundTextureUniform = GLES20.glGetUniformLocation(program, "u_backgroundTexture");
        depthColorTextureUniform = GLES20.glGetUniformLocation(program, "u_depthColorTexture");

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
                    maskTexCoordsBuffer);
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
        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 0");

        // Ensure position is rewound before use.
        backgroundTexCoordsBuffer.position(0);
        maskTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(program);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 1");

        //Binding delle texture
        GLES20.glUniform1i(depthColorTextureUniform, 0);
        GLES20.glUniform1i(backgroundTextureUniform,  2);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getDepthColorTextureId());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 2");

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        GLES20.glVertexAttribPointer(
                maskTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, maskTexCoordsBuffer);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glEnableVertexAttribArray(maskTexCoordAttribute);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 3");

        //Binding del coefficiente: Devo farlo falso per evitare colorazioni nel framebuffer.
        GLES20.glUniform1f(depthColorEnabledUniform, 0.0f);

        //Disegno nel framebuffer
        //https://stackoverflow.com/questions/4041682/android-opengl-es-framebuffer-objects-rendering-depth-buffer-to-texture
        //https://github.com/google-ar/sceneform-android-sdk/issues/225
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 4");

        //Binding del coefficiente:
        GLES20.glUniform1f(depthColorEnabledUniform, depthColorEnabled ? 1.0f : 0.0f);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw 5");

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glDisableVertexAttribArray(maskTexCoordAttribute);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
    }

    public void loadBackgroundImage(Bitmap image){
        loadImage(image, 0, getBackgroundTextureId(), GLES20.GL_TEXTURE2, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
    }

    /**
     * Carica l'immagine di depth. Non libera la risorsa bitmap.
     * @param image bitmap con l'informazione della depth.
     */
    public void loadDepthColorImage(Bitmap image){
        loadImage(image, 0, getDepthColorTextureId(), GLES20.GL_TEXTURE0, GLES20.GL_TEXTURE_2D);
    }

    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine bitmap da caricare
     * @param rotation rotazione da imporre alla maschera
     * @param textureId bind della texture openGL
     * @param activeTexture bind della texture attiva openGL
     */
    private void loadImage(Bitmap image, int rotation, int textureId, int activeTexture, int textureTarget){
        Bitmap flipped = image;

        //https://stackoverflow.com/questions/4518689/texture-coordinates-in-opengl-android-showing-image-reversed
        if(rotation != 0){
            Matrix flip = new Matrix();
            flip.postRotate(rotation);
            flipped = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), flip, true);
        }

        GLES20.glActiveTexture(activeTexture);
        GLES20.glBindTexture(textureTarget, textureId);

        GLUtils.texImage2D(textureTarget, 0, flipped, 0);

        GLES20.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "mask loading");

        // Recycle the bitmap, since its data has been loaded into OpenGL.
        //Faccio il recycle solo del bitmap interno
        if(flipped != image)
            flipped.recycle();
    }
}
