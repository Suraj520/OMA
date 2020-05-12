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
package it.unibo.cvlab.computescene.rendering;

import android.opengl.Matrix;
import it.unibo.cvlab.computescene.dataset.Point;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.FloatBuffer;

/** Renders a point cloud. */
public class PointCloudRenderer {
    private static final String TAG = PointCloudRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "point_cloud.vert";
    private static final String FRAGMENT_SHADER_NAME = "point_cloud.frag";

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.
    private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
    private static final int INITIAL_BUFFER_POINTS = 1000;

    private int vbo;
    private int vboSize;

    private int programName;
    private int positionAttribute;
    private int modelViewProjectionUniform;
    private int colorUniform;
    private int pointSizeUniform;

    private int numPoints = 0;

    public PointCloudRenderer() {}

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer. Must be called on the
     * OpenGL thread, typically in GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig).
     *
     */
    public void createOnGlThread() throws IOException {
        ShaderUtil.checkGLError(TAG, "before create");

        int[] buffers = new int[1];
        GL30.glGenBuffers(buffers);
        vbo = buffers[0];
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);

        vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vboSize, GL30.GL_DYNAMIC_DRAW);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "buffer alloc");

        int vertexShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int passthroughShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        programName = GL30.glCreateProgram();
        GL30.glAttachShader(programName, vertexShader);
        GL30.glAttachShader(programName, passthroughShader);
        GL30.glLinkProgram(programName);
        GL30.glUseProgram(programName);

        ShaderUtil.checkGLError(TAG, "program");

        positionAttribute = GL30.glGetAttribLocation(programName, "a_Position");
        colorUniform = GL30.glGetUniformLocation(programName, "u_Color");
        modelViewProjectionUniform = GL30.glGetUniformLocation(programName, "u_ModelViewProjection");
        pointSizeUniform = GL30.glGetUniformLocation(programName, "u_PointSize");

        ShaderUtil.checkGLError(TAG, "program  params");
    }

    private FloatBuffer pointsToBuffer(Point[] points){

        int length = 0;

        for (Point p:points) {
            if(p != null) length++;
        }

        FloatBuffer data = BufferUtils.createFloatBuffer(length * FLOATS_PER_POINT);

        for (int i = 0; i < length; i++) {
            if(points[i] == null) continue;

            data.put(points[i].getTx());
            data.put(points[i].getTy());
            data.put(points[i].getTz());
            data.put(points[i].getConfidence());
        }

        data.rewind();

        return data;
    }

    /**
     * Updates the OpenGL buffer contents to the provided point. Repeated calls with the same point
     * cloud will be ignored.
     */
    public void update(Point[] cloud) {
        ShaderUtil.checkGLError(TAG, "before update");

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);

        // If the VBO is not large enough to fit the new point cloud, resize it.
        FloatBuffer cloudPoints = pointsToBuffer(cloud);
        numPoints = cloudPoints.remaining() / FLOATS_PER_POINT;

        if (numPoints * BYTES_PER_POINT > vboSize) {
            while (numPoints * BYTES_PER_POINT > vboSize) {
                vboSize *= 2;
            }
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vboSize, GL30.GL_DYNAMIC_DRAW);
        }

        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, cloudPoints);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "after update");
    }

    /**
     * Renders the point cloud. ARCore point cloud is given in world space.
     *
     * @param cameraView the camera view matrix for this frame
     * @param cameraPerspective the camera projection matrix for this frame
     */
    public void draw(float[] cameraView, float[] cameraPerspective) {
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

        ShaderUtil.checkGLError(TAG, "Before draw");

        GL30.glUseProgram(programName);

        GL30.glEnableVertexAttribArray(positionAttribute);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        GL30.glVertexAttribPointer(positionAttribute, 4, GL30.GL_FLOAT, false, BYTES_PER_POINT, 0);

        GL30.glUniform4f(colorUniform, 0.0f / 255.0f, 255.0f / 255.0f, 0.0f / 255.0f, 1.0f);
        GL30.glUniformMatrix4fv(modelViewProjectionUniform, false, modelViewProjection);
        GL30.glUniform1f(pointSizeUniform, 5.0f);
        GL30.glPointSize(5.0f);

        GL30.glDrawArrays(GL30.GL_POINTS, 0, numPoints);

        GL30.glDisableVertexAttribArray(positionAttribute);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Draw");
    }
}
