package it.unibo.cvlab.computescene.rendering;

import android.opengl.Matrix;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import it.unibo.cvlab.computescene.dataset.Pose;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.*;
import java.util.logging.Logger;

/** Renders an object loaded from an OBJ file in OpenGL. */
public class ObjectRenderer {
    private static final String TAG = ObjectRenderer.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(ObjectRenderer.class.getSimpleName());

    public static final float DEFAULT_LOWER_DELTA = 0.00f;
    public static final float DEFAULT_OBJ_SCALE_FACTOR = 1.0f;

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "object.vert";
    private static final String FRAGMENT_SHADER_NAME = "object.frag";

    private static final int COORDS_PER_VERTEX = 3;
    private static final float[] DEFAULT_COLOR = new float[] {0.5f, 0.5f, 0.5f, 1.0f};

    // Object vertex buffer variables.
    private int vertexBufferId;
    private int verticesBaseAddress;
    private int texCoordsBaseAddress;
    private int indexBufferId;
    private int indexCount;

    private int program;
    private final int[] textures = new int[2];

    private int getTextureId(){
        return textures[0];
    }

    private int getInferenceTexture(){
        return textures[1];
    }


    // Shader location: model view projection matrix.
    private int modelUniform;
    private int modelViewProjectionUniform;

    // Shader location: object attributes.
    private int positionAttribute;
    private int texCoordAttribute;

    // Shader location: texture sampler.
    private int textureUniform;

    // Shader location: object color property (to change the primary color of the object).
    private int colorUniform;

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];


    private boolean maskEnabled;
    private int maskEnabledUniform;
    private int inferenceTextureUniform;
    private int scaleFactorUniform;
    private float scaleFactor;

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public void setMaskEnabled(boolean maskEnabled) {
        this.maskEnabled = maskEnabled;
    }

    private float objScaleFactor = DEFAULT_OBJ_SCALE_FACTOR;

    public void setObjScaleFactor(float objScaleFactor){
        this.objScaleFactor = objScaleFactor;
    }

    private float lowerDelta = DEFAULT_LOWER_DELTA;
    private int lowerDeltaUniform;

    public void setLowerDelta(float lowerDelta) {
        this.lowerDelta = lowerDelta;
    }

    public float getLowerDelta() {
        return lowerDelta;
    }

    private int windowSizeUniform;
    private int[] screenData = new int[4];

    private int cameraPoseUniform;
    private float[] cameraPose = new float[3];

    public void setCameraPose(Pose cameraPose){
        this.cameraPose[0] = cameraPose.getTx();
        this.cameraPose[1] = cameraPose.getTy();
        this.cameraPose[2] = cameraPose.getTz();
    }

    public ObjectRenderer() {}

    /**
     * Creates and initializes OpenGL resources needed for rendering the model.
     */
    public void createOnGlThread(Obj obj, BufferedImage textureImage)
            throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        program = GL30.glCreateProgram();
        GL30.glAttachShader(program, vertexShader);
        GL30.glAttachShader(program, fragmentShader);
        GL30.glLinkProgram(program);
        GL30.glUseProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        modelUniform = GL30.glGetUniformLocation(program, "u_model");
        modelViewProjectionUniform = GL30.glGetUniformLocation(program, "u_modelViewProjection");

        positionAttribute = GL30.glGetAttribLocation(program, "a_position");
        texCoordAttribute = GL30.glGetAttribLocation(program, "a_texCoord");

        textureUniform = GL30.glGetUniformLocation(program, "u_texture");
        inferenceTextureUniform = GL30.glGetUniformLocation(program, "u_inferenceTexture");

        maskEnabledUniform = GL30.glGetUniformLocation(program, "u_maskEnabled");

        lowerDeltaUniform = GL30.glGetUniformLocation(program, "u_lowerDelta");

        windowSizeUniform = GL30.glGetUniformLocation(program, "u_windowSize");

        scaleFactorUniform = GL30.glGetUniformLocation(program, "u_scaleFactor");
        cameraPoseUniform = GL30.glGetUniformLocation(program, "u_cameraPose");

        colorUniform = GL30.glGetUniformLocation(program, "u_objColor");

        ShaderUtil.checkGLError(TAG, "Program parameters");

        // Read the texture.

        GL30.glGenTextures(textures);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getTextureId());

        GL30.glTexParameteri(
                GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR_MIPMAP_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        loadTextureImage(textureImage);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getTextureId());
        GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        //Init custom textures
        GL30.glActiveTexture(GL30.GL_TEXTURE1);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTexture());

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
        // that OpenGL understands.

        // Obtain the data from the OBJ, as direct buffers:
        IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
        FloatBuffer vertices = ObjData.getVertices(obj);
        FloatBuffer texCoords = ObjData.getTexCoords(obj, 2);

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
        GL30.glGenBuffers(buffers);
        vertexBufferId = buffers[0];
        indexBufferId = buffers[1];

        // Load vertex buffer
        verticesBaseAddress = 0;
        texCoordsBaseAddress = verticesBaseAddress + 4 * vertices.limit();
        final int totalBytes = texCoordsBaseAddress + 4 * texCoords.limit();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBufferId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, totalBytes, GL30.GL_STATIC_DRAW);
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, verticesBaseAddress, vertices);
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, texCoordsBaseAddress, texCoords);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        // Load index buffer
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        indexCount = indices.limit();
        GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER, indices, GL30.GL_STATIC_DRAW);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "OBJ buffer load");

        Matrix.setIdentityM(modelMatrix, 0);
    }


    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @see android.opengl.Matrix
     */
    public void updateModelMatrix(float[] modelMatrix) {
        updateModelMatrix(modelMatrix, objScaleFactor);
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see android.opengl.Matrix
     */
    public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
    }

    /**
     * Draws the model.
     *
     * @param cameraView A 4x4 view matrix, in column-major order.
     * @param cameraPerspective A 4x4 projection matrix, in column-major order.
     * @see #updateModelMatrix(float[], float)
     * @see android.opengl.Matrix
     */
    public void draw(float[] cameraView, float[] cameraPerspective) {
        draw(cameraView, cameraPerspective, DEFAULT_COLOR);
    }

    public void draw(
            float[] cameraView,
            float[] cameraPerspective,
            float[] objColor) {

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

        ShaderUtil.checkGLError(TAG, "Before draw");

        GL30.glUseProgram(program);

        // Set the object color property.
        GL30.glUniform4fv(colorUniform, objColor);

        GL30.glUniform1f(scaleFactorUniform, scaleFactor);
        GL30.glUniform3fv(cameraPoseUniform, cameraPose);

        // Attach the object texture.
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getTextureId());
        GL30.glUniform1i(textureUniform, 0);

        //Attach mask texture
        GL30.glActiveTexture(GL30.GL_TEXTURE1);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTexture());
        GL30.glUniform1i(inferenceTextureUniform, 1);

        //Enabled Uniform
        GL30.glUniform1f(maskEnabledUniform, maskEnabled ? 1.0f : 0.0f);

        GL30.glUniform1f(lowerDeltaUniform, lowerDelta);

        GL30.glGetIntegerv(GL30.GL_VIEWPORT, screenData);
        GL30.glUniform2f(windowSizeUniform, screenData[2], screenData[3]);

        // Set the vertex attributes.
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBufferId);

        GL30.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GL30.GL_FLOAT, false, 0, verticesBaseAddress);
        GL30.glVertexAttribPointer(texCoordAttribute, 2, GL30.GL_FLOAT, false, 0, texCoordsBaseAddress);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        // Set the ModelViewProjection matrix in the shader.
        GL30.glUniformMatrix4fv(modelUniform, false, modelMatrix);
        GL30.glUniformMatrix4fv(modelViewProjectionUniform, false, modelViewProjectionMatrix);

        // Enable vertex arrays
        GL30.glEnableVertexAttribArray(positionAttribute);
        GL30.glEnableVertexAttribArray(texCoordAttribute);

        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        GL30.glDrawElements(GL30.GL_TRIANGLES, indexCount, GL30.GL_UNSIGNED_SHORT, 0);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, 0);


        // Disable vertex arrays
        GL30.glDisableVertexAttribArray(positionAttribute);
        GL30.glDisableVertexAttribArray(texCoordAttribute);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE1);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

    //https://community.khronos.org/t/loading-and-reading-a-floating-point-texture/63360
    //http://www.anandmuralidhar.com/blog/android/load-read-texture/
    //https://github.com/anandmuralidhar24/FloatTextureAndroid
    //Metodo per caricare la maschera
    public void loadInference(float[] inferenceArray, int width, int height){
        GL30.glActiveTexture(GL30.GL_TEXTURE1);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTexture());

        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R32F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT, inferenceArray);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
    }

    public void loadTextureImage(BufferedImage image){
        loadTexture(image, getTextureId(), GL30.GL_TEXTURE0);
    }

    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine bitmap da caricare
     * @param textureId bind della texture openGL
     * @param activeTexture bind della texture attiva openGL
     */
    private void loadTexture(BufferedImage image, int textureId, int activeTexture){
        int textureTarget = GL30.GL_TEXTURE_2D;

        GL30.glActiveTexture(activeTexture);
        GL30.glBindTexture(textureTarget, textureId);

        //non si capisce se RGBA o ARGB
        //Lo caccio così
        //Altrimenti nello shader inverto i colori.
        int[] rgb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA, image.getWidth(), image.getHeight(), 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, rgb);

        GL30.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "texture loading");
    }
}
