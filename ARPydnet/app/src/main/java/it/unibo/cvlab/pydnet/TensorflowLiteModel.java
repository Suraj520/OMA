package it.unibo.cvlab.pydnet;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class TensorflowLiteModel extends Model{

    private static final String TAG = TensorflowLiteModel.class.getSimpleName();

    private static final int DATA_SIZE = 4;
    private static boolean useNativeNorm = true;
    private static boolean useNativeTfLite = false;

    static{
        if(useNativeNorm){
            System.loadLibrary("native-norm");

            try{
                testNativeNormFunctions();
            }catch (UnsatisfiedLinkError ex){
                useNativeNorm = false;
                Log.w(TAG, "Normalizzazione nativa non disponibile");
            }
        }

        if(useNativeTfLite){
            System.loadLibrary("native-tflite-model");

            try{
                testNativeTfLiteFunctions();
            }catch (UnsatisfiedLinkError ex){
                useNativeTfLite = false;
                Log.w(TAG, "TFLite nativo non disponibile");
            }
        }
    }

    private static native int testNativeTfLiteFunctions();
    private static native int testNativeNormFunctions();

    // Helper static methods

    /**
     * Carica il modello ottimmizato della pydnet dagli assert.
     *
     * @param assets manager per ottenere gli assert
     * @param modelFilename nome del modello da caricare
     * @return Restituisce un buffer leggibile dagli interpreti
     * @throws IOException Qualche errore nell'apertura del file.
     */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {

        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);

        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private static void fillWithData(int[] in, int width, int height, ByteBuffer out){
        out.rewind();

        final int length = width*height;
        for (int i = 0; i < length ; ++i) {
            final int val = in[i];
            out.putFloat((((val >> 16) & 0xFF))/ 255.0f);
            out.putFloat((((val >> 8) & 0xFF))/255.0f);
            out.putFloat((((val) & 0xFF))/255.0f);
        }

        out.rewind();
    }

    private static void fillWithBuffer(IntBuffer in, int width, int height, ByteBuffer out){
        out.rewind();
        in.rewind();

        final int length = width*height;
        for (int i = 0; i < length ; ++i) {
            final int val = in.get();
            out.putFloat((((val >> 16) & 0xFF))/ 255.0f);
            out.putFloat((((val >> 8) & 0xFF))/255.0f);
            out.putFloat((((val) & 0xFF))/255.0f);
        }

        out.rewind();
        in.rewind();
    }

    private static void fillWithBuffer(ByteBuffer in,  ByteBuffer out){
        in.order(ByteOrder.nativeOrder());
        out.order(ByteOrder.nativeOrder());

        if(useNativeNorm){
            RGBbufferNormalization(in, out, in.capacity(), out.capacity());
        }else{
            final int length = out.remaining();
            for (int i = 0; i < length; i+=3) {
                out.putFloat(in.get()/255.0f);
                out.putFloat(in.get()/255.0f);
                out.putFloat(in.get()/255.0f);
            }
        }


    }

    private static native int RGBbufferNormalization(ByteBuffer in, ByteBuffer out, int inLength, int outLength);
    private static native int RGBAbufferNormalization(ByteBuffer in, ByteBuffer out, int inLength, int outLength);
    private static native int ARGBbufferNormalization(ByteBuffer in, ByteBuffer out, int inLength, int outLength);

    protected Interpreter tfLite;
    private boolean isPrepared = false;

    private ByteBuffer inputBackup;
    protected ByteBuffer input;

    protected ByteBuffer inference;


    public TensorflowLiteModel(Context context, ModelFactory.GeneralModel generalModel, String name, String checkpoint){
        super(context, generalModel, name, checkpoint);

        if(useNativeTfLite){
            try {
                MappedByteBuffer buffer = loadModelFile(context.getAssets(), checkpoint);
                final int status = createInterpreter(buffer, buffer.capacity(), true);

                if(status != 0) throw new RuntimeException("Errore init interprete:"+status);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            Interpreter.Options tfliteOptions = new Interpreter.Options();

            //Opzioni di ottimizzazione: uso di Fp16 invece che Fp32, aumento il numero di threads
//        tfliteOptions.setAllowFp16PrecisionForFp32(true);
        tfliteOptions.setNumThreads(4);

//        addGPUDelegate(tfliteOptions);
//        addNNAPIDelegate(tfliteOptions);

            try {
                MappedByteBuffer buffer = loadModelFile(context.getAssets(), checkpoint);
                this.tfLite = new Interpreter(buffer, tfliteOptions);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private void addGPUDelegate(Interpreter.Options options){
        GpuDelegate delegate = new GpuDelegate();
        options.addDelegate(delegate);
    }

    private void addNNAPIDelegate(Interpreter.Options options){
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
        }
    }

    /**
     * Prepara l'allocazione dei buffer per interagire con pydnet.
     *
     * @param resolution risoluzione dell'imagine da dare a pydnet: decisa a priori dal modello.
     */
    @Override
    public void prepare(Utils.Resolution resolution) {
        this.resolution = resolution;

        //RGB: per ogni canale un float (4 byte) il tutto va moltiplicato per il numero di pixel (W*H)
        input = ByteBuffer.allocateDirect(3 * resolution.getHeight() * resolution.getWidth() * DATA_SIZE);

        //Qui ho solo il canale di profondità: restituisce un float di distanza per ogni pixel.
        inference = ByteBuffer.allocateDirect(resolution.getHeight() * resolution.getWidth() * DATA_SIZE);

        input.order(ByteOrder.nativeOrder());
        inference.order(ByteOrder.nativeOrder());

        isPrepared = true;

    }

    @Override
    public void loadInput(Bitmap bmp) {
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        fillInputByteBufferWithBitmap(bmp);
    }

    public void loadInput(int[] data){
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        fillInputWithData(data);
    }

    /**
     * Inserisce nel buffer in della pydnet l'immagine.
     *
     * @param bitmap Immagine da inserire.
     */
    private void fillInputByteBufferWithBitmap(Bitmap bitmap) {
        //Questo è utilizzato per ricavare i canali dell'immagine.
        int[] intInputPixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(intInputPixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        fillInputWithData(intInputPixels);
    }

    private void fillInputWithData(int[] data){
        // Convert the image to floating point.
        final int width = resolution.getWidth();
        final int height = resolution.getHeight();

        fillWithData(data, width, height, input);
    }

    public void loadInput(ByteBuffer data){
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        fillWithBuffer(data, input);
    }

    public void loadInput(IntBuffer data){
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        // Convert the image to floating point.
        final int width = resolution.getWidth();
        final int height = resolution.getHeight();

        fillWithBuffer(data, width, height, input);
    }

    @NonNull
    public FloatBuffer doInference(Utils.Scale scale){
        return doRawInference(scale).asFloatBuffer();
    }

    @NonNull
    public ByteBuffer doRawInference(Utils.Scale scale){
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        input.rewind();
        inference.rewind();

//        Object[] inputArray = new Object[tfLite.getInputTensorCount()];
//        inputArray[tfLite.getInputIndex(getInputNode("image"))] = input;
//
//        Map<Integer, Object> outputMap = new HashMap<>();
//        outputMap.put(tfLite.getOutputIndex(outputNodes.get(scale)), inference);
//
//        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        if(useNativeTfLite){
            final int status = invoke(input, input.capacity(), inference, inference.capacity());
            if(status != 0) throw new RuntimeException("Errore invoke interprete:"+status);
        }else{
            tfLite.run(input, inference);
        }


        input.rewind();
        inference.rewind();

        return inference;
    }

    public void loadDirect(ByteBuffer input){
        if(inputBackup == null) inputBackup = this.input;
        this.input = input;
    }

    public void restore(){
        if(inputBackup != null)
            this.input = inputBackup;
    }

    public void dispose(){
        super.dispose();

        if(useNativeTfLite){
            deleteInterpreter();
        }else{
            tfLite.close();
        }
    }


    private native int createInterpreter(ByteBuffer modelPath, long modelSize, boolean useGPU);
    private native int invoke(ByteBuffer in, long inSize, ByteBuffer out, long outSize);
    private native int deleteInterpreter();
}
