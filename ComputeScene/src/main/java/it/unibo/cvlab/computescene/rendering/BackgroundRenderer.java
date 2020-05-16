package it.unibo.cvlab.computescene.rendering;

import it.unibo.cvlab.computescene.Utils;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.logging.Logger;

//https://github.com/google-ar/arcore-android-sdk/tree/master/samples/hello_ar_java/app/src/main/java/com/google/ar/core/examples/java/common/rendering
public class BackgroundRenderer {
    private static final String TAG = BackgroundRenderer.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(BackgroundRenderer.class.getSimpleName());

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "screenquad.vert";
    private static final String FRAGMENT_SHADER_NAME = "screenquad.frag";

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

    private FloatBuffer backgroundCoordsBuffer;
    private FloatBuffer backgroundTexCoordsBuffer;

    //Riferimento al programma shader
    private int program;

    //Riferimenti ai Sampler
    private int backgroundTextureUniform;

    //Riferimento alla posizione dello sfondo e della relativa maschera.
    private int backgroundPositionAttribute;

    //Riferimento alle coordinate delle texture.
    private int backgroundTexCoordAttribute;

    private int[] textures = new int[4];

    private int getBackgroundTextureId() {
        return textures[0];
    }

    public int getScreenshotFrameBufferTextureId(){
        return textures[1];
    }

    private int getPlasmaTextureId(){
        return textures[2];
    }

    private int getInferenceTextureId(){
        return textures[3];
    }

    private int inferenceTextureUniform;
    private int plasmaEnabledUniform;
    private int plasmaTextureUniform;
    private int plasmaFactorUniform;

    private boolean plasmaEnabled = false;
    private float plasmaFactor = 1.0f;

    public void setPlasmaEnabled(boolean plasmaEnabled) {
        this.plasmaEnabled = plasmaEnabled;
    }

    public void setPlasmaFactor(float plasmaFactor) {
        this.plasmaFactor = plasmaFactor;
    }

    private int scaleFactorUniform;
    private float scaleFactor;
    private int shiftFactorUniform;
    private float shiftFactor;
    private int maxDepthUniform;
    private float maxDepth;

    public void setShiftFactor(float shiftFactor) {
        this.shiftFactor = shiftFactor;
    }

    public void setMaxDepth(float maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    private int[] frameBuffers = new int[1];

    private int getScreenshotFrameBuffer(){
        return frameBuffers[0];
    }

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
     * the OpenGL thread.
     */
    public void createOnGlThread(int surfaceWidth, int surfaceHeight) throws IOException {
        // Generate the textures.
        GL30.glGenTextures(textures);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getBackgroundTextureId());

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE2);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getPlasmaTextureId());

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        //Carico la texture plasma Width: 256 height: 1
        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGB32F, Utils.PLASMA.length / 3, 1, 0, GL30.GL_RGB, GL30.GL_FLOAT, Utils.PLASMA);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE3);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTextureId());

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di una texture aggiuntiva dove salvare il rendering
        GL30.glGenFramebuffers(frameBuffers);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());

        GL30.glActiveTexture(GL30.GL_TEXTURE1);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId());

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA, surfaceWidth, surfaceHeight, 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, (ByteBuffer)null);

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId(), 0);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) {
            Log.info("Framebuffer caricato correttamente");
        }

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

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

        int vertexShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        // create empty OpenGL ES Program
        program = GL30.glCreateProgram();

        // add the vertex shader to program
        GL30.glAttachShader(program, vertexShader);
        // add the fragment shader to program
        GL30.glAttachShader(program, fragmentShader);
        // creates OpenGL ES program executables
        GL30.glLinkProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        GL30.glUseProgram(program);

        backgroundPositionAttribute = GL30.glGetAttribLocation(program, "a_position");
        backgroundTexCoordAttribute = GL30.glGetAttribLocation(program, "a_backgroundTextCoord");
        backgroundTextureUniform = GL30.glGetUniformLocation(program, "u_backgroundTexture");

        plasmaEnabledUniform = GL30.glGetUniformLocation(program, "u_plasmaEnabled");
        plasmaFactorUniform = GL30.glGetUniformLocation(program, "u_plasmaFactor");
        plasmaTextureUniform = GL30.glGetUniformLocation(program, "u_plasmaTexture");

        scaleFactorUniform = GL30.glGetUniformLocation(program, "u_scaleFactor");
        shiftFactorUniform = GL30.glGetUniformLocation(program, "u_shiftFactor");
        maxDepthUniform = GL30.glGetUniformLocation(program, "u_maxDepth");

        inferenceTextureUniform = GL30.glGetUniformLocation(program, "u_inferenceTexture");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    /**
     * Draws the camera background image.
     */
    public void draw() {
        // Ensure position is rewound before use.
        backgroundCoordsBuffer.position(0);
        backgroundTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GL30.glDisable(GL30.GL_DEPTH_TEST);
        GL30.glDepthMask(false);

        GL30.glUseProgram(program);

        GL30.glUniform1f(plasmaEnabledUniform, plasmaEnabled ? 1.0f : 0.0f);
        GL30.glUniform1f(plasmaFactorUniform, plasmaFactor);
        GL30.glUniform1f(scaleFactorUniform, scaleFactor);
        GL30.glUniform1f(shiftFactorUniform, shiftFactor);
        GL30.glUniform1f(maxDepthUniform, maxDepth);


        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getBackgroundTextureId());
        GL30.glUniform1i(backgroundTextureUniform, 0);

        //Attach plasma texture
        GL30.glActiveTexture(GL30.GL_TEXTURE2);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getPlasmaTextureId());
        GL30.glUniform1i(plasmaTextureUniform, 2);

        //Attach mask texture
        GL30.glActiveTexture(GL30.GL_TEXTURE3);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTextureId());
        GL30.glUniform1i(inferenceTextureUniform, 3);

        // Set the vertex positions.
        GL30.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GL30.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GL30.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GL30.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        // Enable vertex arrays
        GL30.glEnableVertexAttribArray(backgroundPositionAttribute);
        GL30.glEnableVertexAttribArray(backgroundTexCoordAttribute);

        //Disegno nel framebuffer
        //https://stackoverflow.com/questions/4041682/android-opengl-es-framebuffer-objects-rendering-depth-buffer-to-texture
        //https://github.com/google-ar/sceneform-android-sdk/issues/225
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GL30.glDrawArrays(GL30.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glDrawArrays(GL30.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GL30.glDisableVertexAttribArray(backgroundPositionAttribute);
        GL30.glDisableVertexAttribArray(backgroundTexCoordAttribute);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE2);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE3);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GL30.glDepthMask(true);
        GL30.glEnable(GL30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
    }

    //https://community.khronos.org/t/loading-and-reading-a-floating-point-texture/63360
    //http://www.anandmuralidhar.com/blog/android/load-read-texture/
    //https://github.com/anandmuralidhar24/FloatTextureAndroid
    //Metodo per caricare la maschera
    public void loadInference(float[] inferenceArray, int width, int height){
        GL30.glActiveTexture(GL30.GL_TEXTURE3);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getInferenceTextureId());

        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R32F, width, height, 0, GL30.GL_RED, GL30.GL_FLOAT, inferenceArray);

        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
    }

    /**
     * Carica l'immagine di background.
     * @param image immagine di sfondo.
     */
    public void loadBackgroudImage(BufferedImage image){
        loadTexture(image, getBackgroundTextureId(), GL30.GL_TEXTURE0);
    }

    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine da caricare
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

        ShaderUtil.checkGLError(TAG, "mask loading");
    }

}
