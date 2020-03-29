package it.unibo.cvlab.scenegenerator;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;

import com.google.ar.core.examples.java.common.rendering.ShaderUtil;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ScreenshotRenderer {

    private static final String TAG = ScreenshotRenderer.class.getSimpleName();

    //https://stackoverflow.com/questions/22909429/android-save-a-bitmap-to-bmp-file-format

    //Due byte fissi 0x42 0x4D (2 bytes)
    //un intero per la dimensione del file (4 bytes)
    //Due short riservati (4 bytes)
    //un intero image data offset 0x36 (4bytes)
    //altro intero size 0x28 (4bytes)
    //intero width (4bytes)
    //intero height (4bytes)
    //short con i piani (2bytes)
    //short bit count (2bytes)
    //intero bit compression (4 bytes)
    //intero imagedatasize (4bytes)
    //4 interi per risoluzione pixel in metri (16bytes)

    //Totale: 70 bytes

    private static final int BITMAP_HEADER_LENGTH = 54;
    private static final byte[] BITMAP_HEADER = new byte[BITMAP_HEADER_LENGTH];

    static{
        //Inizializzazione header bitmap
        BITMAP_HEADER[0] = 0x42;
        BITMAP_HEADER[1] = 0x4D;

        //2,3,4,5 intero dimensione file

        //Due short riservati
        BITMAP_HEADER[6] = 0x00;
        BITMAP_HEADER[7] = 0x00;
        BITMAP_HEADER[8] = 0x00;
        BITMAP_HEADER[9] = 0x00;

        //image data offset
        BITMAP_HEADER[10] = 0x36;
        BITMAP_HEADER[11] = 0x00;
        BITMAP_HEADER[12] = 0x00;
        BITMAP_HEADER[13] = 0x00;

        //size int
        BITMAP_HEADER[14] = 0x28;
        BITMAP_HEADER[15] = 0x00;
        BITMAP_HEADER[16] = 0x00;
        BITMAP_HEADER[17] = 0x00;

        //18,19,20,21 width
        //22,23,24,25 height

        //Short planes
        BITMAP_HEADER[26] = 0x01;
        BITMAP_HEADER[27] = 0x00;

        //bit count short
        BITMAP_HEADER[28] = 0x18;
        BITMAP_HEADER[29] = 0x00;

        //bit compression
        BITMAP_HEADER[30] = 0x00;
        BITMAP_HEADER[31] = 0x00;
        BITMAP_HEADER[32] = 0x00;
        BITMAP_HEADER[33] = 0x00;

        //34,35,36,37 image data size

        //horizontal resolution in pixels per meter
        BITMAP_HEADER[38] = 0x00;
        BITMAP_HEADER[39] = 0x00;
        BITMAP_HEADER[40] = 0x00;
        BITMAP_HEADER[41] = 0x00;

        //vertical resolution in pixels per meter (unreliable)
        BITMAP_HEADER[42] = 0x00;
        BITMAP_HEADER[43] = 0x00;
        BITMAP_HEADER[44] = 0x00;
        BITMAP_HEADER[45] = 0x00;

        BITMAP_HEADER[46] = 0x00;
        BITMAP_HEADER[47] = 0x00;
        BITMAP_HEADER[48] = 0x00;
        BITMAP_HEADER[49] = 0x00;

        BITMAP_HEADER[50] = 0x00;
        BITMAP_HEADER[51] = 0x00;
        BITMAP_HEADER[52] = 0x00;
        BITMAP_HEADER[53] = 0x00;
    }

    private static final int BMP_WIDTH_OF_TIMES = 4;
    //RGBA8
    private static final int BYTE_PER_PIXEL = 4;

    private static byte[] writeInt(int value) {
        byte[] b = new byte[4];

        b[0] = (byte)(value & 0x000000FF);
        b[1] = (byte)((value & 0x0000FF00) >> 8);
        b[2] = (byte)((value & 0x00FF0000) >> 16);
        b[3] = (byte)((value & 0xFF000000) >> 24);

        return b;
    }

    private static void setBitmapHeader(int width, int height){
        //source image width * number of bytes to encode one pixel.
        final int rowWidthInBytes = BYTE_PER_PIXEL * width;
        //the number of bytes used in the file to store raw image data (excluding file headers)
        final int imageSize = rowWidthInBytes * height;
        //file headers size
        final int imageDataOffset = 0x36;
        //final size of the file
        final int fileSize = imageSize + imageDataOffset;

        final byte[] fileSizeBytes = writeInt(fileSize);

        BITMAP_HEADER[2] = fileSizeBytes[0];
        BITMAP_HEADER[3] = fileSizeBytes[1];
        BITMAP_HEADER[4] = fileSizeBytes[2];
        BITMAP_HEADER[5] = fileSizeBytes[3];

        final byte[] widthBytes = writeInt(width);

        BITMAP_HEADER[18] = widthBytes[0];
        BITMAP_HEADER[19] = widthBytes[1];
        BITMAP_HEADER[20] = widthBytes[2];
        BITMAP_HEADER[21] = widthBytes[3];

        final byte[] heightBytes = writeInt(height);

        BITMAP_HEADER[22] = heightBytes[0];
        BITMAP_HEADER[23] = heightBytes[1];
        BITMAP_HEADER[24] = heightBytes[2];
        BITMAP_HEADER[25] = heightBytes[3];

        final byte[] imageSizeBytes = writeInt(imageSize);

        BITMAP_HEADER[34] = imageSizeBytes[0];
        BITMAP_HEADER[35] = imageSizeBytes[1];
        BITMAP_HEADER[36] = imageSizeBytes[2];
        BITMAP_HEADER[37] = imageSizeBytes[3];

    }

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

    private int headerLength = BITMAP_HEADER_LENGTH;
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
    public void createOnGlThread(Context context, Size res) throws IOException {
        this.scaledWidth = res.width;
        this.scaledHeight = res.height;

        this.bufferLength = (scaledHeight * scaledWidth * 4) + headerLength;

        //Inizializzo l'header bitmap
        setBitmapHeader(scaledWidth, scaledHeight);

        //Creo un buffer di precaricamento dei pixelbuffer
        ByteBuffer initDataBuffer = ByteBuffer.allocateDirect(bufferLength);
        initDataBuffer.order(ByteOrder.nativeOrder());
        initDataBuffer.put(BITMAP_HEADER);

        //Genero il renderbuffer dove salvare lo screenshot.

        GLES30.glGenRenderbuffers(renderBuffers.length, renderBuffers, 0);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, getScreenshotRenderBuffer());
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, scaledWidth, scaledHeight);
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
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferLength, initDataBuffer, GLES30.GL_STREAM_READ);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, getScreenshotPixelBufferOdd());
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferLength, initDataBuffer, GLES30.GL_STREAM_READ);
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
        GLES30.glUniform1i(textureUniform, 1);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
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

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        // Restore the depth state for further drawing.
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererDraw");
    }

    //Il bytebuffer è già in formato BMP
    public ByteBuffer getPBO(){
        //http://www.songho.ca/opengl/gl_pbo.html
        //Update index
        index = (index + 1) % 2;
        int nextIndex = (index + 1) % 2;

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, getScreenshotFrameBuffer());
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffers[index]);

        //Read from pixel buffer
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);

        // Read the pixels from pixel buffer.
        GLES30.glReadPixels(0, 0, scaledWidth, scaledHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, headerLength);

        //Mappo il prossimo pixel buffer, da convertire per il modello.
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffers[nextIndex]);

        final Buffer rawBuffer = GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER, headerLength, bufferLength, GLES30.GL_MAP_READ_BIT);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererSavePBO");

        //https://stackoverflow.com/questions/29921348/safe-usage-of-glmapbufferrange-on-android-java
        if(rawBuffer != null){
            return (ByteBuffer) rawBuffer;
        }
        return null;
    }

    public void restore(){
        //Restoring
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glReadBuffer(GLES30.GL_BACK);

        ShaderUtil.checkGLError(TAG, "ScreenshotRendererRestore");
    }

}
