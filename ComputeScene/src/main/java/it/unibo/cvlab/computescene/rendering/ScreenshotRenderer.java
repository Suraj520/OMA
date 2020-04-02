package it.unibo.cvlab.computescene.rendering;

import org.lwjgl.opengles.GLES30;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScreenshotRenderer {
    private static final String TAG = ScreenshotRenderer.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(ScreenshotRenderer.class.getSimpleName());

    private static final String VERTEX_SHADER_NAME = "screenshot.vert";
    private static final String FRAGMENT_SHADER_NAME = "screenshot.frag";

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

    private FloatBuffer screenshotCoordsBuffer;
    private FloatBuffer screenshotTexCoordsBuffer;

    //Riferimento al programma shader
    private int program;

    //Riferimenti ai Sampler
    private int textureUniform;

    //Riferimento alla posizione dello sfondo e della relativa maschera.
    private int positionAttribute;

    //Riferimento alle coordinate delle texture.
    private int texCoordAttribute;

    private int[] textures = new int[1];

    public void setSourceTextureId(int id){
        textures[0] = id;
    }

    private int getSourceTextureId(){
        return textures[0];
    }

    private int[] frameBuffers = new int[1];

    public int getScreenshotFrameBuffer(){
        return frameBuffers[0];
    }

    public int getDefaultFrameBuffer(){
        return 0;
    }

    private int[] renderBuffers = new int[1];

    private int getScreenshotRenderBuffer(){
        return renderBuffers[0];
    }

    private int surfaceWidth, surfaceHeight;
    private int scaledWidth, scaledHeight;

    private byte[] pixelData;
    private ByteBuffer pixelDataBuffer;

    /**
     * Allocates and initializes OpenGL resources needed by the screenshot renderer. Must be called on
     * the OpenGL thread.
     *
     */
    public void createOnGlThread(int surfaceWidth, int surfaceHeight, int scaledWidth, int scaledHeight) throws IOException {
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;

        int bufferLength = scaledWidth * scaledHeight * 4;

        pixelData = new byte[bufferLength];
        pixelDataBuffer = ByteBuffer.wrap(pixelData);
        pixelDataBuffer.order(ByteOrder.nativeOrder());

        //Genero il renderbuffer dove salvare lo screenshot.
        GLES30.glGenRenderbuffers(renderBuffers);

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, getScreenshotRenderBuffer());
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, scaledWidth, scaledHeight);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "RenderBuffer loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di un renderbuffer aggiuntivo dove salvare il rendering
        GLES30.glGenFramebuffers(frameBuffers);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, getScreenshotRenderBuffer());

        //Check sullo stato del framebuffer
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.log(Level.INFO, "Framebuffer caricato correttamente");
        }else{
            throw new RuntimeException("Impossibile caricare il framebuffer");
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Framebuffer loading");

        int numVertices = 4;

        if (numVertices != VERTEX_COUNT) {
            throw new RuntimeException("Unexpected number of vertices in BackgroundRenderer.");
        }

        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());

        screenshotCoordsBuffer = bbCoords.asFloatBuffer();
        screenshotCoordsBuffer.put(QUAD_COORDS);
        screenshotCoordsBuffer.position(0);

        ByteBuffer bbTexCoordsTransformed =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder());

        screenshotTexCoordsBuffer = bbTexCoordsTransformed.asFloatBuffer();
        screenshotTexCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        screenshotTexCoordsBuffer.rewind();

        int vertexShader =
                ShaderUtil.loadGLShader(TAG, GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        // create empty OpenGL ES Program
        program = GLES30.glCreateProgram();

        // add the vertex shader to program
        GLES30.glAttachShader(program, vertexShader);
        // add the fragment shader to program
        GLES30.glAttachShader(program, fragmentShader);
        // creates OpenGL ES program executables
        GLES30.glLinkProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        GLES30.glUseProgram(program);

        positionAttribute = GLES30.glGetAttribLocation(program, "a_position");
        texCoordAttribute = GLES30.glGetAttribLocation(program, "a_textCoord");

        textureUniform = GLES30.glGetUniformLocation(program, "u_texture");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    public void drawOnPBO(){
        // Ensure position is rewound before use.
        screenshotCoordsBuffer.position(0);
        screenshotTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        GLES30.glUseProgram(program);

        //Binding delle texture
        GLES30.glUniform1i(textureUniform, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, getSourceTextureId());

        // Set the vertex positions.
        GLES30.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, screenshotCoordsBuffer);

        // Set the texture coordinates.
        GLES30.glVertexAttribPointer(
                texCoordAttribute, TEXCOORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, screenshotTexCoordsBuffer);

        // Enable vertex arrays
        GLES30.glEnableVertexAttribArray(positionAttribute);
        GLES30.glEnableVertexAttribArray(texCoordAttribute);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES30.glViewport(0, 0, scaledWidth, scaledHeight);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        // Disable vertex arrays
        GLES30.glDisableVertexAttribArray(positionAttribute);
        GLES30.glDisableVertexAttribArray(texCoordAttribute);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererDraw");
    }


    public ByteBuffer getByteBufferScreenshot(int frameBufferId){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBufferId);

        // Read the pixels from pixel buffer.
        GLES30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelDataBuffer);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSave");

        //Restoring
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");

        return pixelDataBuffer;
    }

    public byte[] getByteArrayScreenshot(int frameBufferId){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBufferId);

        // Read the pixels from pixel buffer.
        GLES30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelDataBuffer);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSave");

        //Restoring
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");

        return pixelData;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }
}
