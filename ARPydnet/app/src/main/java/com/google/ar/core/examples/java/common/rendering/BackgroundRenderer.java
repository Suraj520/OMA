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
    private static final int FLOAT_SIZE = 4;

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
//    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private FloatBuffer backgroundCoordsBuffer;
    private FloatBuffer clipBackgroundCoordsBuffer;
    private FloatBuffer backgroundTexCoordsBuffer;
    private FloatBuffer maskTexCoordsBuffer;

    //Riferimento al programma shader
    private int backgroundProgram;

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

    private int[] textures = new int[2];

    public int getDepthColorTextureId(){
        return textures[0];
    }

    public int getBackgroundTextureId() {
        return textures[1];
    }

    private boolean depthColorEnabled = false;

    public boolean isDepthColorEnabled() {
        return depthColorEnabled;
    }

    public void setDepthColorEnabled(boolean depthColorEnabled) {
        this.depthColorEnabled = depthColorEnabled;
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

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(textureTarget, getBackgroundTextureId());

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        int numVertices = 4;

        if (numVertices != VERTEX_COUNT) {
            throw new RuntimeException("Unexpected number of vertices in BackgroundRenderer.");
        }

        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());

        backgroundCoordsBuffer = bbCoords.asFloatBuffer();
        backgroundCoordsBuffer.put(QUAD_COORDS);
        backgroundCoordsBuffer.position(0);

        ByteBuffer bbCoords2 = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());

        clipBackgroundCoordsBuffer = bbCoords2.asFloatBuffer();
        clipBackgroundCoordsBuffer.put(QUAD_COORDS);
        clipBackgroundCoordsBuffer.position(0);

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
        backgroundProgram = GLES20.glCreateProgram();

        // add the vertex shader to program
        GLES20.glAttachShader(backgroundProgram, vertexShader);
        // add the fragment shader to program
        GLES20.glAttachShader(backgroundProgram, fragmentShader);
        // creates OpenGL ES program executables
        GLES20.glLinkProgram(backgroundProgram);

        ShaderUtil.checkGLError(TAG, "Program creation");

        GLES20.glUseProgram(backgroundProgram);

        backgroundPositionAttribute = GLES20.glGetAttribLocation(backgroundProgram, "a_position");
        backgroundTexCoordAttribute = GLES20.glGetAttribLocation(backgroundProgram, "a_backgroundTextCoord");
        maskTexCoordAttribute = GLES20.glGetAttribLocation(backgroundProgram, "a_maskTextCoord");

        depthColorEnabledUniform = GLES20.glGetUniformLocation(backgroundProgram, "u_depthColorEnabled");
        backgroundTextureUniform = GLES20.glGetUniformLocation(backgroundProgram, "u_backgroundTexture");
        depthColorTextureUniform = GLES20.glGetUniformLocation(backgroundProgram, "u_depthColorTexture");

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
     * Draws the camera image using the currently configured {@link BackgroundRenderer#backgroundTexCoordsBuffer}
     * image texture coordinates.
     *
     * <p>The image will be center cropped if the camera sensor aspect ratio does not match the screen
     * aspect ratio, which matches the cropping behavior of {@link
     * Frame#transformCoordinates2d(Coordinates2d, float[], Coordinates2d, float[])}.
     */
    public void draw(
            int imageWidth, int imageHeight, float screenAspectRatio, int cameraToDisplayRotation) {
        // Crop the camera image to fit the screen aspect ratio.
        float imageAspectRatio = (float) imageWidth / imageHeight;
        float croppedWidth;
        float croppedHeight;
        if (screenAspectRatio < imageAspectRatio) {
            croppedWidth = imageHeight * screenAspectRatio;
            croppedHeight = imageHeight;
        } else {
            croppedWidth = imageWidth;
            croppedHeight = imageWidth / screenAspectRatio;
        }

        float u = (imageWidth - croppedWidth) / imageWidth * 0.5f;
        float v = (imageHeight - croppedHeight) / imageHeight * 0.5f;

        float[] texCoordTransformed;
        switch (cameraToDisplayRotation) {
            case 90:
                texCoordTransformed = new float[] {1 - u, 1 - v, u, 1 - v, 1 - u, v, u, v};
                break;
            case 180:
                texCoordTransformed = new float[] {1 - u, v, 1 - u, 1 - v, u, v, u, 1 - v};
                break;
            case 270:
                texCoordTransformed = new float[] {u, v, 1 - u, v, u, 1 - v, 1 - u, 1 - v};
                break;
            case 0:
                texCoordTransformed = new float[] {u, 1 - v, u, v, 1 - u, 1 - v, 1 - u, v};
                break;
            default:
                throw new IllegalArgumentException("Unhandled rotation: " + cameraToDisplayRotation);
        }

        // Write image texture coordinates.
        backgroundTexCoordsBuffer.position(0);
        backgroundTexCoordsBuffer.put(texCoordTransformed);

        draw();
    }

    /**
     * Draws the camera background image using the currently configured {@link
     * BackgroundRenderer#backgroundTexCoordsBuffer} image texture coordinates.
     */
    private void draw() {
        // Ensure position is rewound before use.
        backgroundTexCoordsBuffer.position(0);
        maskTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(backgroundProgram);

        //Binding del coefficiente:
        GLES20.glUniform1f(depthColorEnabledUniform, depthColorEnabled ? 1.0f : 0.0f);

        //Binding delle texture
        GLES20.glUniform1i(depthColorTextureUniform, 0);
        GLES20.glUniform1i(backgroundTextureUniform,  1);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getDepthColorTextureId());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        GLES20.glVertexAttribPointer(
                maskTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, maskTexCoordsBuffer);

        //Debug

//        backgroundCoordsBuffer.rewind();
//        backgroundTexCoordsBuffer.rewind();
//        maskTexCoordsBuffer.rewind();
//
//        float[] backgroundCoordsArray = new float[backgroundCoordsBuffer.remaining()];
//        float[] backgroundTexCoordsArray = new float[backgroundTexCoordsBuffer.remaining()];
//        float[] maskTexCoordsArray = new float[maskTexCoordsBuffer.remaining()];
//
//        for(int i = 0; i < backgroundCoordsArray.length && backgroundCoordsBuffer.hasRemaining(); i++) backgroundCoordsArray[i] = backgroundCoordsBuffer.get();
//        for(int i = 0; i < backgroundTexCoordsArray.length && backgroundTexCoordsBuffer.hasRemaining(); i++) backgroundTexCoordsArray[i] = backgroundTexCoordsBuffer.get();
//        for(int i = 0; i < maskTexCoordsArray.length && maskTexCoordsBuffer.hasRemaining(); i++) maskTexCoordsArray[i] = maskTexCoordsBuffer.get();

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glEnableVertexAttribArray(maskTexCoordAttribute);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttribute);
        GLES20.glDisableVertexAttribArray(maskTexCoordAttribute);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
    }

//    public void loadBackgroundImage(Bitmap image){
//        loadMask(image, 0, getBackgroundTextureId(), GLES20.GL_TEXTURE1);
//    }

    /**
     * Carica l'immagine di depth. Non libera la risorsa bitmap.
     * @param image bitmap con l'informazione della depth.
     */
    public void loadDepthColorImage(Bitmap image){
        loadDepthColorImage(image, 0);
    }

    /**
     * Carica l'immagine di depth. Non libera la risorsa bitmap.
     * @param image bitmap con l'informazione della depth.
     * @param rotation rotazione da imprimere: 0 di default.
     */
    private void loadDepthColorImage(Bitmap image, int rotation){
        loadMask(image, rotation, getDepthColorTextureId(), GLES20.GL_TEXTURE0);
    }

    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine bitmap da caricare
     * @param rotation rotazione da imporre alla maschera
     * @param textureId bind della texture openGL
     * @param activeTexture bind della texture attiva openGL
     */
    private void loadMask(Bitmap image, int rotation, int textureId, int activeTexture){
        Bitmap flipped = image;

        //https://stackoverflow.com/questions/4518689/texture-coordinates-in-opengl-android-showing-image-reversed
        if(rotation != 0){
            Matrix flip = new Matrix();
            flip.postRotate(rotation);
            flipped = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), flip, true);
        }

        int textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(activeTexture);
        GLES20.glBindTexture(textureTarget, textureId);

        GLUtils.texImage2D(textureTarget, 0, flipped, 0);

        GLES20.glBindTexture(textureTarget, 0);

        // Recycle the bitmap, since its data has been loaded into OpenGL.
        //Faccio il recycle solo del bitmap interno
        if(flipped != image)
            flipped.recycle();
    }

    public void clipBackgroundTexture(int cameraWidth, int cameraHeight, Utils.Resolution res){
        float dx = 0.0f;
        float dy = 0.0f;

        if(cameraWidth >= res.getWidth()){
            dx = (float)cameraWidth / res.getWidth();
            dx -=1.0f;
        }else{
            Log.d(TAG, "Width pydnet > camera width");
        }

        if(cameraHeight >= res.getHeight()){
            dy = (float)cameraHeight / res.getHeight();
            dy -=1.0f;
        }else{
            Log.d(TAG, "Height pydnet > camera height");
        }

        backgroundCoordsBuffer.rewind();

        int remaining = backgroundCoordsBuffer.remaining();

        float[] array = new float[remaining];
        for(int i = 0; i < array.length && backgroundCoordsBuffer.hasRemaining(); i++) array[i] = backgroundCoordsBuffer.get();


//        -1.0f, -1.0f,   //Bottom left
//        -1.0f, +1.0f,   //Top left
//        +1.0f, -1.0f,   //Bottom right
//        +1.0f, +1.0f,   //Top Right

        array[0] += dx;//Bottom left
        array[1] += dy;//Bottom left
        array[2] += dx;//Top left
        array[5] += dy;//Bottom right

        backgroundCoordsBuffer.rewind();
        clipBackgroundCoordsBuffer.rewind();
        clipBackgroundCoordsBuffer.put(array);
        clipBackgroundCoordsBuffer.rewind();
    }

}
