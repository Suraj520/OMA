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
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import com.google.ar.core.Camera;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unibo.cvlab.pydnet.Utils;

/** Renders the detected AR planes. */
public class PlaneRenderer {
    private static final String TAG = PlaneRenderer.class.getSimpleName();

    public static final float MIN_LOWER_DELTA = -0.50f;
    public static final float MAX_LOWER_DELTA = 0.50f;
    public static final float DEFAULT_LOWER_DELTA = 0.05f;
    public static final float STEP_LOWER_DELTA = 0.02f;

    public static final float MIN_UPPER_DELTA = -0.50f;
    public static final float MAX_UPPER_DELTA = 0.50f;
    public static final float DEFAULT_UPPER_DELTA = 0.10f;
    public static final float STEP_UPPER_DELTA = 0.02f;

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/plane.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/plane.frag";

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int BYTES_PER_SHORT = Short.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3; // x, z, alpha

    private static final int VERTS_PER_BOUNDARY_VERT = 2;
    private static final int INDICES_PER_BOUNDARY_VERT = 3;
    private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 64;

    private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES =
            BYTES_PER_FLOAT * COORDS_PER_VERTEX * VERTS_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS;

    private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES =
            BYTES_PER_SHORT
                    * INDICES_PER_BOUNDARY_VERT
                    * INDICES_PER_BOUNDARY_VERT
                    * INITIAL_BUFFER_BOUNDARY_VERTS;

    private static final float FADE_RADIUS_M = 0.25f;
    private static final float DOTS_PER_METER = 10.0f;
    private static final float EQUILATERAL_TRIANGLE_SCALE = (float) (1 / Math.sqrt(3));

    // Using the "signed distance field" approach to render sharp lines and circles.
    // {dotThreshold, lineThreshold, lineFadeSpeed, occlusionScale}
    // dotThreshold/lineThreshold: red/green intensity above which dots/lines are present
    // lineFadeShrink:  lines will fade in between alpha = 1-(1/lineFadeShrink) and 1.0
    // occlusionShrink: occluded planes will fade out between alpha = 0 and 1/occlusionShrink
    private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f};

    private int planeProgram;
    private final int[] textures = new int[4];

    private int getTextureId(){
        return textures[0];
    }

    private int getInferenceTexture(){
        return textures[1];
    }

    private int getPlasmaTexture(){
        return textures[2];
    }

    private int getRainTexture(){
        return textures[3];
    }

    private int planeXZPositionAlphaAttribute;

    private int planeModelUniform;
    private int planeNormalUniform;
    private int planeModelViewProjectionUniform;
    private int textureUniform;
    private int lineColorUniform;
    private int dotColorUniform;
    private int gridControlUniform;
    private int planeUvMatrixUniform;

    private FloatBuffer vertexBuffer =
            ByteBuffer.allocateDirect(INITIAL_VERTEX_BUFFER_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
    private ShortBuffer indexBuffer =
            ByteBuffer.allocateDirect(INITIAL_INDEX_BUFFER_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();

    // Temporary lists/matrices allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private final float[] planeColor = new float[] {1f, 1f, 1f, 1f};
    private final float[] planeAngleUvMatrix =
            new float[4]; // 2x2 rotation matrix applied to uv coords.

    private final Map<Plane, Integer> planeIndexMap = new HashMap<>();


    //Custom data for Mask
    private int maskEnabledUniform;
    private int inferenceTextureUniform;
    private boolean maskEnabled;

    private int scaleFactorUniform;
    private float scaleFactor;
    private int shiftFactorUniform;
    private float shiftFactor;

    public void setShiftFactor(float shiftFactor) {
        this.shiftFactor = shiftFactor;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public void setMaskEnabled(boolean maskEnabled) {
        this.maskEnabled = maskEnabled;
    }

    private float upperDelta = DEFAULT_LOWER_DELTA, lowerDelta = DEFAULT_LOWER_DELTA;
    private int upperDeltaUniform, lowerDeltaUniform;

    public void setUpperDelta(float upperDelta) {
        this.upperDelta = upperDelta;
    }

    public void setLowerDelta(float lowerDelta) {
        this.lowerDelta = lowerDelta;
    }

    private int plasmaEnabledUniform;
    private int plasmaTextureUniform;

    private boolean plasmaEnabled = false;

    public void setPlasmaEnabled(boolean plasmaEnabled) {
        this.plasmaEnabled = plasmaEnabled;
    }

    private int timeUniform;
    private int rainResolutionUniform;
    private int rainEnabledUniform;
    private int rainTextureUniform;

    private boolean rainEnabled = false;
    private float time = 0.0f;
    private float timeSpeed = 0.05f;
    private float[] rainResolution = new float[]{50000f, 50000f};

    public void setRainEnabled(boolean rainEnabled) {
        this.rainEnabled = rainEnabled;
    }

    public void updateTime(){
        this.time += this.timeSpeed;
    }

    public void setTimeSpeed(float timeSpeed) {
        this.timeSpeed = timeSpeed;
    }

    public void setRainResolution(float[] rainResolution) {
        this.rainResolution = rainResolution;
    }

    private int windowSizeUniform;
    private int[] screenData = new int[4];

    private int cameraPoseUniform;
    private float[] cameraPose = new float[3];

    public void setCameraPose(Pose cameraPose){
        this.cameraPose[0] = cameraPose.tx();
        this.cameraPose[1] = cameraPose.ty();
        this.cameraPose[2] = cameraPose.tz();
    }

    private int screenOrientationUniform;
    private int screenOrientation;

    public void setScreenOrientation(int screenOrientation) {
        this.screenOrientation = screenOrientation;
    }

    private int maxDepthUniform;
    private float maxDepth;


    public void setMaxDepth(float maxDepth) {
        this.maxDepth = maxDepth;
    }

    public PlaneRenderer() {}

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer. Must be called on the
     * OpenGL thread, typically in GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig).
     *
     * @param context Needed to access shader source and texture PNG.
     * @param gridDistanceTextureName Name of the PNG file containing the grid texture.
     */
    public void createOnGlThread(Context context, String gridDistanceTextureName, String rainTextureName) throws IOException {
        int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int passthroughShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        planeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(planeProgram, vertexShader);
        GLES20.glAttachShader(planeProgram, passthroughShader);
        GLES20.glLinkProgram(planeProgram);
        GLES20.glUseProgram(planeProgram);

        ShaderUtil.checkGLError(TAG, "Program creation");

        // Read the texture.
        Bitmap textureBitmap =
                BitmapFactory.decodeStream(context.getAssets().open(gridDistanceTextureName));

        Bitmap rainBitmap =
                BitmapFactory.decodeStream(context.getAssets().open(rainTextureName));

        GLES20.glGenTextures(textures.length, textures, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTextureId());

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        textureBitmap.recycle();

        //Init custom textures
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getInferenceTexture());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPlasmaTexture());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //Carico la texture plasma Width: 256 height: 1
        GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGB32F, Utils.PLASMA.length / 3, 1, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, FloatBuffer.wrap(Utils.PLASMA));

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getRainTexture());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //Carico la texture rain
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, rainBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        rainBitmap.recycle();

        ShaderUtil.checkGLError(TAG, "Texture loading");

        planeXZPositionAlphaAttribute = GLES20.glGetAttribLocation(planeProgram, "a_positionXZAlpha");

        planeModelUniform = GLES20.glGetUniformLocation(planeProgram, "u_model");
        planeNormalUniform = GLES20.glGetUniformLocation(planeProgram, "u_normal");
        planeModelViewProjectionUniform =
                GLES20.glGetUniformLocation(planeProgram, "u_modelViewProjection");
        textureUniform = GLES20.glGetUniformLocation(planeProgram, "u_texture");
        lineColorUniform = GLES20.glGetUniformLocation(planeProgram, "u_lineColor");
        dotColorUniform = GLES20.glGetUniformLocation(planeProgram, "u_dotColor");
        gridControlUniform = GLES20.glGetUniformLocation(planeProgram, "u_gridControl");
        planeUvMatrixUniform = GLES20.glGetUniformLocation(planeProgram, "u_planeUvMatrix");

        //Custom.
        maskEnabledUniform = GLES20.glGetUniformLocation(planeProgram, "u_maskEnabled");
        inferenceTextureUniform = GLES20.glGetUniformLocation(planeProgram, "u_inferenceTexture");

        upperDeltaUniform = GLES20.glGetUniformLocation(planeProgram, "u_upperDelta");
        lowerDeltaUniform = GLES20.glGetUniformLocation(planeProgram, "u_lowerDelta");

        plasmaTextureUniform = GLES20.glGetUniformLocation(planeProgram, "u_plasmaTexture");
        plasmaEnabledUniform = GLES20.glGetUniformLocation(planeProgram, "u_plasmaEnabled");

        timeUniform = GLES20.glGetUniformLocation(planeProgram, "u_time");
        rainEnabledUniform = GLES20.glGetUniformLocation(planeProgram, "u_rainEnabled");
        rainResolutionUniform = GLES20.glGetUniformLocation(planeProgram, "u_rainResolution");
        rainTextureUniform = GLES20.glGetUniformLocation(planeProgram, "u_rainTexture");

        windowSizeUniform = GLES20.glGetUniformLocation(planeProgram, "u_windowSize");

        scaleFactorUniform = GLES20.glGetUniformLocation(planeProgram, "u_scaleFactor");
        shiftFactorUniform = GLES20.glGetUniformLocation(planeProgram, "u_shiftFactor");
        cameraPoseUniform = GLES20.glGetUniformLocation(planeProgram, "u_cameraPose");
        screenOrientationUniform = GLES20.glGetUniformLocation(planeProgram, "u_screenOrientation");

        maxDepthUniform = GLES20.glGetUniformLocation(planeProgram, "u_maxDepth");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    /** Updates the plane model transform matrix and extents. */
    private void updatePlaneParameters(
            float[] planeMatrix, float extentX, float extentZ, FloatBuffer boundary) {
        System.arraycopy(planeMatrix, 0, modelMatrix, 0, 16);
        if (boundary == null) {
            vertexBuffer.limit(0);
            indexBuffer.limit(0);
            return;
        }

        // Generate a new set of vertices and a corresponding triangle strip index set so that
        // the plane boundary polygon has a fading edge. This is done by making a copy of the
        // boundary polygon vertices and scaling it down around center to push it inwards. Then
        // the index buffer is setup accordingly.
        boundary.rewind();
        int boundaryVertices = boundary.limit() / 2;
        int numVertices;
        int numIndices;

        numVertices = boundaryVertices * VERTS_PER_BOUNDARY_VERT;
        // drawn as GL_TRIANGLE_STRIP with 3n-2 triangles (n-2 for fill, 2n for perimeter).
        numIndices = boundaryVertices * INDICES_PER_BOUNDARY_VERT;

        if (vertexBuffer.capacity() < numVertices * COORDS_PER_VERTEX) {
            int size = vertexBuffer.capacity();
            while (size < numVertices * COORDS_PER_VERTEX) {
                size *= 2;
            }
            vertexBuffer =
                    ByteBuffer.allocateDirect(BYTES_PER_FLOAT * size)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer();
        }

        vertexBuffer.rewind();
        vertexBuffer.limit(numVertices * COORDS_PER_VERTEX);

        if (indexBuffer.capacity() < numIndices) {
            int size = indexBuffer.capacity();
            while (size < numIndices) {
                size *= 2;
            }
            indexBuffer =
                    ByteBuffer.allocateDirect(BYTES_PER_SHORT * size)
                            .order(ByteOrder.nativeOrder())
                            .asShortBuffer();
        }

        indexBuffer.rewind();
        indexBuffer.limit(numIndices);

        // Note: when either dimension of the bounding box is smaller than 2*FADE_RADIUS_M we
        // generate a bunch of 0-area triangles.  These don't get rendered though so it works
        // out ok.
        float xScale = Math.max((extentX - 2 * FADE_RADIUS_M) / extentX, 0.0f);
        float zScale = Math.max((extentZ - 2 * FADE_RADIUS_M) / extentZ, 0.0f);

        while (boundary.hasRemaining()) {
            float x = boundary.get();
            float z = boundary.get();
            vertexBuffer.put(x);
            vertexBuffer.put(z);
            vertexBuffer.put(0.0f);
            vertexBuffer.put(x * xScale);
            vertexBuffer.put(z * zScale);
            vertexBuffer.put(1.0f);
        }

        // step 1, perimeter
        indexBuffer.put((short) ((boundaryVertices - 1) * 2));

        for (int i = 0; i < boundaryVertices; ++i) {
            indexBuffer.put((short) (i * 2));
            indexBuffer.put((short) (i * 2 + 1));
        }

        indexBuffer.put((short) 1);
        // This leaves us on the interior edge of the perimeter between the inset vertices
        // for boundary verts n-1 and 0.

        // step 2, interior:
        for (int i = 1; i < boundaryVertices / 2; ++i) {
            indexBuffer.put((short) ((boundaryVertices - 1 - i) * 2 + 1));
            indexBuffer.put((short) (i * 2 + 1));
        }

        if (boundaryVertices % 2 != 0) {
            indexBuffer.put((short) ((boundaryVertices / 2) * 2 + 1));
        }
    }

    private void draw(float[] cameraView, float[] cameraPerspective, float[] planeNormal) {
        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

        // Set the position of the plane
        vertexBuffer.rewind();

        GLES20.glVertexAttribPointer(
                planeXZPositionAlphaAttribute,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                BYTES_PER_FLOAT * COORDS_PER_VERTEX,
                vertexBuffer);

        // Set the Model and ModelViewProjection matrices in the shader.
        GLES20.glUniformMatrix4fv(planeModelUniform, 1, false, modelMatrix, 0);
        GLES20.glUniform3f(planeNormalUniform, planeNormal[0], planeNormal[1], planeNormal[2]);
        GLES20.glUniformMatrix4fv(
                planeModelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

        indexBuffer.rewind();

        GLES20.glDrawElements(
                GLES20.GL_TRIANGLE_STRIP, indexBuffer.limit(), GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        ShaderUtil.checkGLError(TAG, "Drawing plane");
    }

    static class SortablePlane {
        final float distance;
        final Plane plane;

        SortablePlane(float distance, Plane plane) {
            this.distance = distance;
            this.plane = plane;
        }
    }

    /**
     * Draws the collection of tracked planes, with closer planes hiding more distant ones.
     *
     * @param allPlanes The collection of planes to draw.
     * @param cameraPose The pose of the camera, as returned by {@link Camera#getPose()}
     * @param cameraPerspective The projection matrix, as returned by {@link
     *     Camera#getProjectionMatrix(float[], int, float, float)}
     */
    public void drawPlanes(Collection<Plane> allPlanes, Pose cameraPose, float[] cameraPerspective) {
        // Planes must be sorted by distance from camera so that we draw closer planes first, and
        // they occlude the farther planes.
        List<SortablePlane> sortedPlanes = new ArrayList<>();

        for (Plane plane : allPlanes) {
            if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
                continue;
            }

            float distance = calculateDistanceToPlane(plane.getCenterPose(), cameraPose);
            if (distance < 0) { // Plane is back-facing.
                continue;
            }
            sortedPlanes.add(new SortablePlane(distance, plane));
        }

        Collections.sort(
                sortedPlanes,
                (a, b) -> Float.compare(a.distance, b.distance));

        float[] cameraView = new float[16];
        cameraPose.inverse().toMatrix(cameraView, 0);

        // Planes are drawn with additive blending, masked by the alpha channel for occlusion.

        // Start by clearing the alpha channel of the color buffer to 1.0.
        GLES20.glClearColor(1, 1, 1, 1);
        GLES20.glColorMask(false, false, false, true);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glColorMask(true, true, true, true);

        // Disable depth write.
        GLES20.glDepthMask(false);

        // Additive blending, masked by alpha channel, clearing alpha channel.
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_DST_ALPHA, GLES20.GL_ONE, // RGB (src, dest)
                GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA); // ALPHA (src, dest)

        // Set up the shader.
        GLES20.glUseProgram(planeProgram);

        // Attach the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTextureId());
        GLES20.glUniform1i(textureUniform, 0);

        //Attach inference texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getInferenceTexture());
        GLES20.glUniform1i(inferenceTextureUniform, 1);

        //Attach plasma texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPlasmaTexture());
        GLES20.glUniform1i(plasmaTextureUniform, 2);

        //Attach rain texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getRainTexture());
        GLES20.glUniform1i(rainTextureUniform, 3);

        //Enabled Uniform
        GLES20.glUniform1f(maskEnabledUniform, maskEnabled ? 1.0f : 0.0f);
        GLES20.glUniform1f(plasmaEnabledUniform, plasmaEnabled ? 1.0f : 0.0f);
        GLES20.glUniform1f(screenOrientationUniform, screenOrientation);

        GLES20.glUniform1f(upperDeltaUniform, upperDelta);
        GLES20.glUniform1f(lowerDeltaUniform, lowerDelta);

        GLES20.glUniform1f(rainEnabledUniform, rainEnabled ? 1.0f : 0.0f);
        GLES20.glUniform1f(timeUniform, time);
        GLES20.glUniform2f(rainResolutionUniform, rainResolution[0], rainResolution[1]);

        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, screenData, 0);
        GLES20.glUniform2f(windowSizeUniform, screenData[2], screenData[3]);

        GLES20.glUniform1f(scaleFactorUniform, scaleFactor);
        GLES20.glUniform1f(shiftFactorUniform, shiftFactor);
        GLES20.glUniform3fv(cameraPoseUniform, 1, this.cameraPose, 0);

        GLES20.glUniform1f(maxDepthUniform, maxDepth);

        // Shared fragment uniforms.
        GLES20.glUniform4fv(gridControlUniform, 1, GRID_CONTROL, 0);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(planeXZPositionAlphaAttribute);

        ShaderUtil.checkGLError(TAG, "Setting up to draw planes");

        for (SortablePlane sortedPlane : sortedPlanes) {
            Plane plane = sortedPlane.plane;
            float[] planeMatrix = new float[16];
            plane.getCenterPose().toMatrix(planeMatrix, 0);

            float[] normal = new float[3];
            // Get transformed Y axis of plane's coordinate system.
            plane.getCenterPose().getTransformedAxis(1, 1.0f, normal, 0);

            updatePlaneParameters(
                    planeMatrix, plane.getExtentX(), plane.getExtentZ(), plane.getPolygon());

            // Get plane index. Keep a map to assign same indices to same planes.
            Integer planeIndex = planeIndexMap.get(plane);
            if (planeIndex == null) {
                planeIndex = planeIndexMap.size();
                planeIndexMap.put(plane, planeIndex);
            }

            // Set plane color.
            GLES20.glUniform4fv(lineColorUniform, 1, planeColor, 0);
            GLES20.glUniform4fv(dotColorUniform, 1, planeColor, 0);

            // Each plane will have its own angle offset from others, to make them easier to
            // distinguish. Compute a 2x2 rotation matrix from the angle.
            float angleRadians = planeIndex * 0.144f;
            float uScale = DOTS_PER_METER;
            float vScale = DOTS_PER_METER * EQUILATERAL_TRIANGLE_SCALE;
            planeAngleUvMatrix[0] = +(float) Math.cos(angleRadians) * uScale;
            planeAngleUvMatrix[1] = -(float) Math.sin(angleRadians) * vScale;
            planeAngleUvMatrix[2] = +(float) Math.sin(angleRadians) * uScale;
            planeAngleUvMatrix[3] = +(float) Math.cos(angleRadians) * vScale;
            GLES20.glUniformMatrix2fv(planeUvMatrixUniform, 1, false, planeAngleUvMatrix, 0);

            draw(cameraView, cameraPerspective, normal);
        }

        // Clean up the state we set
        GLES20.glDisableVertexAttribArray(planeXZPositionAlphaAttribute);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "Cleaning up after drawing planes");
    }

    // Calculate the normal distance to plane from cameraPose, the given planePose should have y axis
    // parallel to plane's normal, for example plane's center pose or hit test pose.
    public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0]
                + (cameraY - planePose.ty()) * normal[1]
                + (cameraZ - planePose.tz()) * normal[2];
    }

    //https://community.khronos.org/t/loading-and-reading-a-floating-point-texture/63360
    //http://www.anandmuralidhar.com/blog/android/load-read-texture/
    //https://github.com/anandmuralidhar24/FloatTextureAndroid
    //Metodo per caricare la maschera
    public void loadInference(FloatBuffer inference, Utils.Resolution resolution){
        inference.rewind();

        //Anche se in precedenza ho usato GLES20, il contesto è di tipo 3.0, compatibile con i precedenti.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, getInferenceTexture());

        GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R32F, resolution.getWidth(), resolution.getHeight(), 0, GLES30.GL_RED, GLES30.GL_FLOAT, inference);

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    /**
     * Carica l'immagine di depth. Non libera la risorsa bitmap.
     * @param image bitmap con l'informazione della depth.
     */
    public void loadInferenceImage(Bitmap image){
        loadTexture(image, 0, getInferenceTexture(), GLES20.GL_TEXTURE1);
    }


    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine bitmap da caricare
     * @param rotation rotazione da imporre alla maschera
     * @param textureId bind della texture openGL
     * @param activeTexture bind della texture attiva openGL
     */
    private void loadTexture(Bitmap image, int rotation, int textureId, int activeTexture){
        Bitmap flipped = image;

        //https://stackoverflow.com/questions/4518689/texture-coordinates-in-opengl-android-showing-image-reversed
        if(rotation != 0){
            android.graphics.Matrix flip = new android.graphics.Matrix();
            flip.postRotate(rotation);
            flipped = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), flip, true);
        }

        int textureTarget = GLES20.GL_TEXTURE_2D;

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
