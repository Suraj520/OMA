package it.unibo.cvlab.arpydnet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.google.ar.core.examples.java.common.rendering.ShaderUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class PointerRenderer {
    private static final String TAG = PointerRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/pointer.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/pointer.frag";

    private static final int COORDS_PER_VERTEX = 2;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int FLOAT_SIZE = Float.BYTES;

    private static final float[] QUAD_COORDS =
            new float[] {
                    -0.1f, -0.1f,   //Bottom left
                    -0.1f, +0.1f,   //Top left
                    +0.1f, -0.1f,   //Bottom right
                    +0.1f, +0.1f,   //Top Right
            };

    private static final float[] QUAD_TEXTURE_COORDS =
            new float[] {
                    0.0f, 0.0f,   //Bottom left
                    0.0f, 1.0f,   //Top left
                    1.0f, 0.0f,   //Bottom right
                    1.0f, 1.0f,   //Top Right
            };

    private static final int VERTEX_COUNT = QUAD_COORDS.length / COORDS_PER_VERTEX;

    private FloatBuffer coordsBuffer;
    private FloatBuffer texCoordsBuffer;

    private int program;

    private int modelMatrixUniform;
    private int textureUniform;

    private int posAttribute;
    private int textCoordAttribute;

    private int[] textures = new int[1];

    public int getPointerTextureId(){
        return textures[0];
    }

    private float[] modelMatrix = new float[16];

    //Inizializzo le variabili per posizionare il puntatore al centro.
    private int widthUniform, heightUniform;
    private int width, height;
    private int x, y;

    private float scaleFactor;

    public PointerRenderer(){
        this(1.0f);
    }

    public PointerRenderer(float scaleFactor){
        this(scaleFactor, 50,50,100,100);
    }

    public PointerRenderer(float scaleFactor, int x, int y, int width, int height){
        this.scaleFactor = scaleFactor;
        initModelMatrix(x,y,width,height);
    }

    public void setXY(int x, int y) {
        this.x = x;
        this.y = y;
        updateModelMatrix();
    }

    public void setResolution(int width, int height) {
        this.width = width;
        this.height = height;
        updateModelMatrix();
    }

    private void initModelMatrix(int x, int y, int width, int height){
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updateModelMatrix();
    }

    private void updateModelMatrix(){
        //Le coordinate opengl sono riferite al centro.
        //Ho il punto centrale dove posizionare il puntatore.
        //L'origine opengl è bottom-left mentre quello android è top-left

        //Trasformo il punto centrale nelle coordinate opengl
        float relativeX = (float)x/width;
        float relativeY = (float)y/height;

        //Devo invertire la y per portarmi in un'origine bottom-left
        relativeY = 1.0f - relativeY;

        //Devo portare i punti relativi da 0.0,1.0 a -1.0,1.0
        relativeX = 2*relativeX-1;
        relativeY = 2*relativeY-1;

        //Posso creare la matrice seguendo:
        //http://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/#translation-matrices

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.scaleM(modelMatrix, 0, scaleFactor, scaleFactor, 1.0f);
        Matrix.translateM(modelMatrix, 0, relativeX, relativeY, 0);
    }

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
     * the OpenGL thread.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context, String textureName) throws IOException {
        // Generate the textures.
        GLES20.glGenTextures(textures.length, textures, 0);

        // Read the texture.
        Bitmap textureBitmap =
                BitmapFactory.decodeStream(context.getAssets().open(textureName));

        int textureTarget = GLES20.GL_TEXTURE_2D;

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(textureTarget, getPointerTextureId());

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLUtils.texImage2D(textureTarget, 0, textureBitmap, 0);
        GLES20.glGenerateMipmap(textureTarget);

        GLES20.glBindTexture(textureTarget, 0);

        textureBitmap.recycle();

        ShaderUtil.checkGLError(TAG, "Texture loading");

        int numVertices = 4;

        if (numVertices != VERTEX_COUNT) {
            throw new RuntimeException("Unexpected number of vertices in BackgroundRenderer.");
        }

        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());

        coordsBuffer = bbCoords.asFloatBuffer();
        coordsBuffer.put(QUAD_COORDS);
        coordsBuffer.position(0);

        ByteBuffer bbTexCoordsTransformed =
                ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder());

        texCoordsBuffer = bbTexCoordsTransformed.asFloatBuffer();
        texCoordsBuffer.put(QUAD_TEXTURE_COORDS);
        texCoordsBuffer.rewind();


        int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

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

        posAttribute = GLES20.glGetAttribLocation(program, "a_pos");
        textCoordAttribute = GLES20.glGetAttribLocation(program, "a_textCoord");

        modelMatrixUniform = GLES20.glGetUniformLocation(program, "u_modelMatrix");
        textureUniform = GLES20.glGetUniformLocation(program, "u_texture");

        widthUniform = GLES20.glGetUniformLocation(program, "u_width");
        heightUniform = GLES20.glGetUniformLocation(program, "u_height");

        ShaderUtil.checkGLError(TAG, "Program parameters");
    }

    public void draw(){
        coordsBuffer.position(0);
        texCoordsBuffer.position(0);

        // No need to test or write depth
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        // Additive blending, masked by alpha channel, clearing alpha channel.
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_DST_ALPHA, GLES20.GL_ONE, // RGB (src, dest)
                GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA); // ALPHA (src, dest)

        GLES20.glUseProgram(program);

        GLES20.glUniform1i(textureUniform, 0);

        GLES20.glUniform1i(widthUniform, width);
        GLES20.glUniform1i(heightUniform, height);

        GLES20.glUniformMatrix4fv(modelMatrixUniform, 1, false, modelMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPointerTextureId());

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
                posAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, coordsBuffer);

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
                textCoordAttribute, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, texCoordsBuffer);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(posAttribute);
        GLES20.glEnableVertexAttribArray(textCoordAttribute);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(posAttribute);
        GLES20.glDisableVertexAttribArray(textCoordAttribute);

        //Restore blend state
        GLES20.glDisable(GLES20.GL_BLEND);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        ShaderUtil.checkGLError(TAG, "PointerRenderer Draw");
    }



}
