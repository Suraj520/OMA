package it.unibo.cvlab.arpydnet;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLES30;
import android.util.Log;

import com.google.ar.core.examples.java.common.rendering.ShaderUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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

    private int[] textures = new int[2];

    private int getScaledColorFrameBufferTextureId(){
        return textures[0];
    }

    public void setColorFrameBufferTextureId(int id){
        textures[1] = id;
    }

    private int getColorFrameBufferTextureId(){
        return textures[1];
    }

    private int[] frameBuffers = new int[1];

    private int getScaledScreenshotFrameBuffer(){
        return frameBuffers[0];
    }

    private int surfaceWidth, surfaceHeight;
    private int scaledWidth, scaledHeight;

    private int[] pixelData;
    private IntBuffer pixelDataBuffer;

//    private ByteBuffer pixelDataBuffer;

    public void onSurfaceChanged(int width, int height){
        surfaceWidth = width;
        surfaceHeight = height;
    }

    public void updateScaledFrameBuffer(Utils.Resolution res){
        updateScaledFrameBuffer(res.getWidth(), res.getHeight());
    }

    public void updateScaledFrameBuffer(int width, int height){
        this.scaledWidth = width;
        this.scaledHeight = height;

        pixelData = new int[width * height];
        pixelDataBuffer = IntBuffer.wrap(pixelData);

//        pixelDataBuffer = IntBuffer.allocate(width * height);
//        pixelDataBuffer = ByteBuffer.allocate(width * height * 3);
//        pixelDataBuffer.order(ByteOrder.nativeOrder());

        //Devo aggiornare anche la texture associata al framebuffer
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScaledScreenshotFrameBuffer());

        int textureTarget = GLES30.GL_TEXTURE_2D;

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(textureTarget, getScaledColorFrameBufferTextureId());

        //glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 800, 600, 0, GL_RGB, GL_UNSIGNED_BYTE, NULL);
        //Salvo il buffer finale come float così non devo fare altre conversioni.
        //Se voglio creare un byte buffer RGB senza alpha usa GL_RGBA
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);

        ShaderUtil.checkGLError(TAG, "Framebuffer texture allocate");

        GLES30.glBindTexture(textureTarget, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    /**
     * Allocates and initializes OpenGL resources needed by the screenshot renderer. Must be called on
     * the OpenGL thread.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) throws IOException {
        // Generate the textures.
        GLES30.glGenTextures(textures.length, textures, 0);

        int textureTarget = GLES30.GL_TEXTURE_2D;

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(textureTarget, getScaledColorFrameBufferTextureId());

        GLES30.glTexParameteri(textureTarget, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(textureTarget, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        GLES30.glTexParameteri(textureTarget, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(textureTarget, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);

        GLES30.glBindTexture(textureTarget, 0);

        ShaderUtil.checkGLError(TAG, "Texture loading");

        //Creazione del framebuffer per il rendering alternativo alla finestra.
        //Ho bisogno anche di una texture aggiuntiva dove salvare il rendering
        GLES30.glGenFramebuffers(frameBuffers.length, frameBuffers, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScaledScreenshotFrameBuffer());

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(textureTarget, getScaledColorFrameBufferTextureId());

        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, getScaledColorFrameBufferTextureId(), 0);

        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "Framebuffer caricato correttamente");
        }

        GLES30.glBindTexture(textureTarget, 0);
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

    public int[] screenshot(){
        // Ensure position is rewound before use.
        screenshotCoordsBuffer.position(0);
        screenshotTexCoordsBuffer.position(0);
        pixelDataBuffer.position(0);

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        GLES30.glUseProgram(program);

        //Binding delle texture
        GLES30.glUniform1i(textureUniform, 1);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, getColorFrameBufferTextureId());

        // Set the vertex positions.
        GLES30.glVertexAttribPointer(
                positionAttribute, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, screenshotCoordsBuffer);

        // Set the texture coordinates.
        GLES30.glVertexAttribPointer(
                texCoordAttribute, TEXCOORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, screenshotTexCoordsBuffer);

        // Enable vertex arrays
        GLES30.glEnableVertexAttribArray(positionAttribute);
        GLES30.glEnableVertexAttribArray(texCoordAttribute);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScaledScreenshotFrameBuffer());
        GLES30.glViewport(0, 0, scaledWidth, scaledHeight);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        //Se voglio creare un byte buffer RGB senza alpha usa glPixelStorei
//        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);

        // Read the pixels from framebuffer.
        GLES30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelDataBuffer);

//        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4);

        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        // Disable vertex arrays
        GLES30.glDisableVertexAttribArray(positionAttribute);
        GLES30.glDisableVertexAttribArray(texCoordAttribute);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererDraw");

        return pixelData;
    }

}
