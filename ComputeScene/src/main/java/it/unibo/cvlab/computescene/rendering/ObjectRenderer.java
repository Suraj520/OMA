package it.unibo.cvlab.computescene.rendering;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import org.lwjgl.opengles.GLES20;
import silvertiger.tutorial.lwjgl.math.Matrix4f;

import java.io.IOException;
import java.nio.*;
import java.util.HashMap;
import java.util.logging.Logger;

public class ObjectRenderer {
    private static final String TAG = ObjectRenderer.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(BackgroundRenderer.class.getSimpleName());

    private static final float DEFAULT_OBJ_SCALE_FACTOR = 1.0f;

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "object.vert";
    private static final String FRAGMENT_SHADER_NAME = "object.frag";

    private static final int COORDS_PER_VERTEX = 3;

    private int program;

    // Shader location: model view projection matrix.
    private int modelViewProjectionUniform;

    // Shader location: object attributes.
    private int positionAttribute;

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private Matrix4f modelMatrix = new Matrix4f();

    private float objScaleFactor = DEFAULT_OBJ_SCALE_FACTOR;

    public void setObjScaleFactor(float objScaleFactor){
        this.objScaleFactor = objScaleFactor;
    }

    private class MyObject{
        // Object vertex buffer variables.
        int vertexBufferId;
        int verticesBaseAddress;
        int indexBufferId;
        int indexCount;

        int[] getBuffers(){
            return new int[]{vertexBufferId, indexBufferId};
        }
    }

    private HashMap<String, MyObject> objMap = new HashMap<>();
    private String currentObj;

    public void setCurrentObj(String currentObj){
        if(objMap.containsKey(currentObj)){
            this.currentObj = currentObj;
        }
    }

    public void loadObj(String name, Obj obj){
        MyObject myObject = new MyObject();

        // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
        // that OpenGL understands.

        // Obtain the data from the OBJ, as direct buffers:
        IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
        FloatBuffer vertices = ObjData.getVertices(obj);

        // Convert int indices to shorts for GL ES 2.0 compatibility
        ShortBuffer indices =
                ByteBuffer.allocateDirect(2 * wideIndices.limit())
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer();
        while (wideIndices.hasRemaining()) {
            indices.put((short) wideIndices.get());
        }
        indices.rewind();

        int[] buffers = new int[2];
        GLES20.glGenBuffers(buffers);
        myObject.vertexBufferId = buffers[0];
        myObject.indexBufferId = buffers[1];

        // Load vertex buffer
        myObject.verticesBaseAddress = 0;
        final int totalBytes = myObject.verticesBaseAddress + 4 * vertices.limit();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, myObject.vertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, myObject.verticesBaseAddress, vertices);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, myObject.indexBufferId);
        myObject.indexCount = indices.limit();
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "OBJ buffer load");

        if(objMap.isEmpty()) currentObj = name;
        objMap.put(name, myObject);
    }

    public void releaseObj(String name){
        if(objMap.containsKey(name)){
            if(currentObj != null && currentObj.equals(name)) currentObj = null;
            MyObject myObject = objMap.get(name);
            GLES20.glDeleteBuffers(myObject.getBuffers());
        }
    }

    public void createOnGlThread()
            throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_modelViewProjection");
        positionAttribute = GLES20.glGetAttribLocation(program, "a_position");

        ShaderUtil.checkGLError(TAG, "Program parameters");

        modelMatrix.setIdentity();
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     */
    public void updateModelMatrix(float[] modelMatrix) {
        updateModelMatrix(modelMatrix, objScaleFactor);
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see Matrix4f
     */
    private void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        this.modelMatrix = new Matrix4f(modelMatrix);
        this.modelMatrix.multiply(scaleFactor);
    }

    public void draw(float[] cameraView, float[] cameraPerspective) {
        if(currentObj == null) return;

        MyObject obj = objMap.get(currentObj);

        ShaderUtil.checkGLError(TAG, "Before draw");

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix4f modelViewMatrix = new Matrix4f(cameraView);
        modelViewMatrix.multiply(modelMatrix);

        Matrix4f modelViewProjectionMatrix = new Matrix4f(cameraPerspective);
        modelViewProjectionMatrix.multiply(modelViewMatrix);

        GLES20.glUseProgram(program);

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, obj.vertexBufferId);

        GLES20.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, obj.verticesBaseAddress);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, false, modelViewProjectionMatrix.toArray());

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionAttribute);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, obj.indexBufferId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, obj.indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionAttribute);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
