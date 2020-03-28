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

import androidx.annotation.NonNull;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


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
    private FloatBuffer backgroundTexCoordsBuffer;

    //Riferimento al programma shader
    private int backgroundProgram;

    //Riferimenti ai Sampler
    private int backgroundTextureUniform;

    //Riferimento alla posizione dello sfondo e della relativa maschera.
    private int backgroundPositionAttribute;

    //Riferimento alle coordinate delle texture.
    private int backgroundTexCoordAttribute;

    private int[] textures = new int[1];

    public int getBackgroundTextureId() {
        return textures[0];
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
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

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

        ByteBuffer bbTexCoordsTransformed =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder());

        backgroundTexCoordsBuffer = bbTexCoordsTransformed.asFloatBuffer();
        backgroundTexCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        backgroundTexCoordsBuffer.rewind();


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

        backgroundTextureUniform = GLES20.glGetUniformLocation(backgroundProgram, "u_backgroundTexture");

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
        // Ensure position is rewound before use.
        backgroundTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(backgroundProgram);

        GLES20.glUniform1i(backgroundTextureUniform,  0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getBackgroundTextureId());

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttribute);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttribute);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
    }



}
