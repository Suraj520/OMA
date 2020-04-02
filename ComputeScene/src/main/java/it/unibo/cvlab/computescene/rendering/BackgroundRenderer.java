package it.unibo.cvlab.computescene.rendering;

import org.lwjgl.opengles.GLES20;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

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

    private int[] textures = new int[2];

    private int getBackgroundTextureId() {
        return textures[0];
    }

    public int getScreenshotFrameBufferTextureId(){
        return textures[1];
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
        GLES20.glGenTextures(textures);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getBackgroundTextureId());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di una texture aggiuntiva dove salvare il rendering
        GLES20.glGenFramebuffers(frameBuffers);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId());

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, surfaceWidth, surfaceHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, (ByteBuffer)null);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getScreenshotFrameBufferTextureId(), 0);

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.info("Framebuffer caricato correttamente");
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


        int vertexShader =
                ShaderUtil.loadGLShader(TAG, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

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

        backgroundTextureUniform = GLES20.glGetUniformLocation(program, "u_backgroundTexture");

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
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(program);

        GLES20.glUniform1i(backgroundTextureUniform,  0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getBackgroundTextureId());

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                backgroundPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundCoordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                backgroundTexCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, backgroundTexCoordsBuffer);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttribute);

        //Disegno nel framebuffer
        //https://stackoverflow.com/questions/4041682/android-opengl-es-framebuffer-objects-rendering-depth-buffer-to-texture
        //https://github.com/google-ar/sceneform-android-sdk/issues/225
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(backgroundPositionAttribute);
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttribute);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "BackgroundRendererDraw");
    }


    /**
     * Carica l'immagine di background.
     * @param image immagine di sfondo.
     */
    public void loadBackgroudImage(BufferedImage image){
        loadTexture(image, getBackgroundTextureId(), GLES20.GL_TEXTURE0);
    }

    /**
     * Carica un immagine in una texture openGL.
     *
     * @param image immagine da caricare
     * @param textureId bind della texture openGL
     * @param activeTexture bind della texture attiva openGL
     */
    private void loadTexture(BufferedImage image, int textureId, int activeTexture){
        int textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(activeTexture);
        GLES20.glBindTexture(textureTarget, textureId);

        //non si capisce se RGBA o ARGB
        //Lo caccio così
        //Altrimenti nello shader inverto i colori.
        int[] rgb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, image.getWidth(), image.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgb);

        GLES20.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "mask loading");
    }

}
