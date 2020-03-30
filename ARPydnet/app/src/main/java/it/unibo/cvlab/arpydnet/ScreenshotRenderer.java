package it.unibo.cvlab.arpydnet;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;

import com.google.ar.core.examples.java.common.rendering.ShaderUtil;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import it.unibo.cvlab.pydnet.Model;
import it.unibo.cvlab.pydnet.Utils;

public class ScreenshotRenderer {

    private static final String TAG = ScreenshotRenderer.class.getSimpleName();

    private static final String VERTEX_SHADER_NAME = "shaders/screenshot.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/screenshot.frag";

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

    private int getScreenshotFrameBuffer(){
        return frameBuffers[0];
    }

    private int[] renderBuffers = new int[1];

    private int getScreenshotRenderBuffer(){
        return renderBuffers[0];
    }

    private int[] pixelBuffers = new int[2];

    private int getScreenshotPixelBufferEven(){
        return pixelBuffers[0];
    }

    private int getScreenshotPixelBufferOdd(){
        return pixelBuffers[1];
    }

    private int surfaceWidth, surfaceHeight;
    private int scaledWidth, scaledHeight;

//    private int[] pixelData;
//    private IntBuffer pixelDataBuffer;

    private int bufferLength;

    private int index = 0;


    public void onSurfaceChanged(int width, int height){
        surfaceWidth = width;
        surfaceHeight = height;
    }

    /**
     * Allocates and initializes OpenGL resources needed by the screenshot renderer. Must be called on
     * the OpenGL thread.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context, Utils.Resolution res) throws IOException {
        this.scaledWidth = res.getWidth();
        this.scaledHeight = res.getHeight();
        this.bufferLength = scaledHeight * scaledWidth * 3;

        //Buffer dove mettere i dati dello screenshot.
//        pixelData = new int[scaledHeight * scaledWidth];
//        pixelDataBuffer = IntBuffer.wrap(pixelData);

        //Genero il renderbuffer dove salvare lo screenshot.

        GLES30.glGenRenderbuffers(renderBuffers.length, renderBuffers, 0);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, getScreenshotRenderBuffer());
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGB8, scaledWidth, scaledHeight);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "RenderBuffer loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di un renderbuffer aggiuntivo dove salvare il rendering
        GLES30.glGenFramebuffers(frameBuffers.length, frameBuffers, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, getScreenshotRenderBuffer());

        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "Framebuffer caricato correttamente");
        }else{
            throw new RuntimeException("Impossibile caricare il framebuffer");
        }

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Framebuffer loading");

        //Genero il Pixel Buffer Object per ottimizzare
        GLES30.glGenBuffers(pixelBuffers.length, pixelBuffers, 0);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, getScreenshotPixelBufferEven());
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferLength, null, GLES30.GL_STREAM_READ);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, getScreenshotPixelBufferOdd());
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferLength, null, GLES30.GL_STREAM_READ);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);


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
                ShaderUtil.loadGLShader(TAG, context, GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

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

    //Per la modifica in ByteBuffer
    //Restituisci ByteBuffer. Controlla che sia sufficientemente grande.

    public void screenshot(Model model){
        // Ensure position is rewound before use.
        screenshotCoordsBuffer.position(0);
        screenshotTexCoordsBuffer.position(0);
//        pixelDataBuffer.position(0);

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

        //http://www.songho.ca/opengl/gl_pbo.html
        //Update index
        index = (index + 1) % 2;
        int nextIndex = (index + 1) % 2;

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffers[index]);

        //Read from pixel buffer
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);

        // Read the pixels from pixel buffer.
        //Se voglio creare un byte buffer RGB senza alpha usa glPixelStorei
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        GLES30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, 0);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4);

        //Mappo il prossimo pixel buffer, da convertire per il modello.
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffers[nextIndex]);

        final Buffer rawBuffer = GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER, 0, bufferLength, GLES30.GL_MAP_READ_BIT);

        //https://stackoverflow.com/questions/29921348/safe-usage-of-glmapbufferrange-on-android-java
        if(rawBuffer != null){
            ByteBuffer byteBuffer = (ByteBuffer) rawBuffer;
            model.loadInput(byteBuffer);
        }

        //Restoring
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glReadBuffer(GLES30.GL_BACK);

        ShaderUtil.checkGLError(TAG, "Screenshot");
    }

}
