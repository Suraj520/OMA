package it.unibo.cvlab.computescene.rendering;

import org.lwjgl.opengl.GL30;

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

    public enum ColorType{
        RGBA8(GL30.GL_RGBA, GL30.GL_RGBA8, 4, 4),
        RGB8(GL30.GL_RGB, GL30.GL_RGB8, 3, 1),
        R8(GL30.GL_RED, GL30.GL_R8, 1, 1);

        private int openglType;
        private int openglTypeFormat;
        private int byteSize;
        private int pixelStoreAlignment;

        ColorType(int openglType, int openglTypeFormat, int byteSize, int pixelStoreAlignment) {
            this.openglType = openglType;
            this.openglTypeFormat = openglTypeFormat;
            this.byteSize = byteSize;
            this.pixelStoreAlignment = pixelStoreAlignment;
        }

        public int getOpenglType() {
            return openglType;
        }

        public int getOpenglTypeFormat() {
            return openglTypeFormat;
        }

        public int getByteSize() {
            return byteSize;
        }

        public int getPixelStoreAlignment() {
            return pixelStoreAlignment;
        }

        public static ColorType parseByteSize(int byteSize){
            switch (byteSize){
                case 4:
                    return RGBA8;
                case 3:
                    return RGB8;
                case 1:
                    return R8;
                default:
                    throw new IllegalArgumentException("Dimensione non supportata");
            }
        }
    }

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

    private float[] pixelDataFloatArray;
    private int[] pixelDataIntArray;
    private ByteBuffer pixelDataBuffer;

    private ColorType colorType;

    /**
     * Allocates and initializes OpenGL resources needed by the screenshot renderer. Must be called on
     * the OpenGL thread.
     *
     */
    public void createOnGlThread(ColorType colorType, int surfaceWidth, int surfaceHeight, int scaledWidth, int scaledHeight) throws IOException {
        this.colorType = colorType;
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;

        int bufferLength = scaledWidth * scaledHeight * colorType.getByteSize();

        pixelDataBuffer = ByteBuffer.allocateDirect(bufferLength);
        pixelDataBuffer.order(ByteOrder.nativeOrder());

        pixelDataIntArray = new int[bufferLength];
        pixelDataFloatArray = new float[bufferLength];

        //Genero il renderbuffer dove salvare lo screenshot.
        GL30.glGenRenderbuffers(renderBuffers);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, getScreenshotRenderBuffer());
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, colorType.getOpenglTypeFormat(), scaledWidth, scaledHeight);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "RenderBuffer loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di un renderbuffer aggiuntivo dove salvare il rendering
        GL30.glGenFramebuffers(frameBuffers);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, getScreenshotRenderBuffer());

        //Check sullo stato del framebuffer
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) {
            Log.log(Level.INFO, "Framebuffer caricato correttamente");
        }else{
            throw new RuntimeException("Impossibile caricare il framebuffer");
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

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

        positionAttribute = GL30.glGetAttribLocation(program, "a_position");
        texCoordAttribute = GL30.glGetAttribLocation(program, "a_textCoord");

        textureUniform = GL30.glGetUniformLocation(program, "u_texture");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    public void drawOnPBO(){
        // Ensure position is rewound before use.
        screenshotCoordsBuffer.position(0);
        screenshotTexCoordsBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GL30.glDisable(GL30.GL_DEPTH_TEST);
        GL30.glDepthMask(false);

        GL30.glUseProgram(program);

        //Binding delle texture
        GL30.glUniform1i(textureUniform, 0);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, getSourceTextureId());

        // Set the vertex positions.
        GL30.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GL30.GL_FLOAT, false, 0, screenshotCoordsBuffer);

        // Set the texture coordinates.
        GL30.glVertexAttribPointer(
                texCoordAttribute, TEXCOORDS_PER_VERTEX, GL30.GL_FLOAT, false, 0, screenshotTexCoordsBuffer);

        // Enable vertex arrays
        GL30.glEnableVertexAttribArray(positionAttribute);
        GL30.glEnableVertexAttribArray(texCoordAttribute);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GL30.glViewport(0, 0, scaledWidth, scaledHeight);
        GL30.glDrawArrays(GL30.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        GL30.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        // Disable vertex arrays
        GL30.glDisableVertexAttribArray(positionAttribute);
        GL30.glDisableVertexAttribArray(texCoordAttribute);

        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GL30.glDepthMask(true);
        GL30.glEnable(GL30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererDraw");
    }

    public ByteBuffer getByteBufferScreenshot(int frameBufferId){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);

        // Read the pixels from pixel buffer.
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, colorType.getPixelStoreAlignment());
        GL30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                colorType.getOpenglType(), GL30.GL_FLOAT, pixelDataBuffer);
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 4);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSave");

        //Restoring
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");

        return pixelDataBuffer;
    }

    public int[] getIntArrayScreenshot(int frameBufferId){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);

        // Read the pixels from pixel buffer.
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, colorType.getPixelStoreAlignment());
        GL30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                colorType.getOpenglType(), GL30.GL_FLOAT, pixelDataIntArray);
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 4);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSave");

        //Restoring
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");

        return pixelDataIntArray;
    }

    public float[] getFloatArrayScreenshot(int frameBufferId){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);

        // Read the pixels from pixel buffer.
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, colorType.getPixelStoreAlignment());
        GL30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                colorType.getOpenglType(), GL30.GL_FLOAT, pixelDataFloatArray);
        GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 4);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSave");

        //Restoring
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");

        return pixelDataFloatArray;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }
}
