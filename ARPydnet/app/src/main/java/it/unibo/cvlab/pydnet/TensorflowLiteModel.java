package it.unibo.cvlab.pydnet;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TensorflowLiteModel extends Model{

    private static final String TAG = TensorflowLiteModel.class.getSimpleName();

    protected Interpreter tfLite;
    private int[] intInputPixels;
    private boolean isPrepared = false;


    public TensorflowLiteModel(Context context, ModelFactory.GeneralModel generalModel, String name, String checkpoint){
        super(context, generalModel, name, checkpoint);


        //Ora la GPU non è supportata.
//        GpuDelegate delegate = new GpuDelegate();
//        Interpreter.Options tfliteOptions = new Interpreter.Options().addDelegate(delegate);

        Interpreter.Options tfliteOptions = new Interpreter.Options();

        //Opzioni di ottimizzazione: uso di Fp16 invece che Fp32, aumento il numero di threads
        //e cerco di usare le API di Android per ANN.
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate nnApiDelegate = new NnApiDelegate();
            tfliteOptions.addDelegate(nnApiDelegate);
        }

        tfliteOptions.setAllowFp16PrecisionForFp32(true);
        tfliteOptions.setNumThreads(4);
//        tfliteOptions.setUseNNAPI(true);

        try {
            MappedByteBuffer buffer = loadModelFile(context.getAssets(), checkpoint);
            this.tfLite = new Interpreter(buffer, tfliteOptions);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prepara l'allocazione dei buffer per interagire con pydnet.
     *
     * @param resolution risoluzione dell'imagine da dare a pydnet: decisa a priori dal modello.
     */
    @Override
    public void prepare(Utils.Resolution resolution) {
        //RGB: per ogni canale un float (4 byte) il tutto va moltiplicato per il numero di pixel (W*H)
        input = ByteBuffer.allocateDirect(3 * resolution.getHeight() * resolution.getWidth() * 4);
        //Qui ho solo il canale di profondità: restituisce un float di distanza per ogni pixel.
        inference = ByteBuffer.allocateDirect(resolution.getHeight() * resolution.getWidth() * 4);
        //Si tratta del buffer di sopra normalizzato
        normalizedInference = ByteBuffer.allocateDirect(resolution.getHeight() * resolution.getWidth() * 4);
        //Questo è utilizzato per ricavare i canali dell'immagine.
        intInputPixels = new int[resolution.getWidth() * resolution.getHeight()];

        input.order(ByteOrder.nativeOrder());
        inference.order(ByteOrder.nativeOrder());
        normalizedInference.order(ByteOrder.nativeOrder());

        isPrepared = true;

    }

    @Override
    public void loadInput(Bitmap bmp) {
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        input.rewind();
        fillInputByteBufferWithBitmap(bmp);
        input.rewind();
    }

    @NonNull
    public FloatBuffer doInference(Utils.Scale scale){
        if (!isPrepared) {
            throw new RuntimeException("Model is not prepared.");
        }

        input.rewind();
        inference.rewind();

        //L'ottmizzazione riduce la rete piramidale ad un solo layer di uscita:
        //non ho più bisogno di mappare le uscite e utilizzo il metodo più semplice.

//        Object[] inputArray = new Object[tfLite.getInputTensorCount()];
//        inputArray[tfLite.getInputIndex(getInputNode("image"))] = inputByteBuffer;
//
//        Map<Integer, Object> outputMap = new HashMap<>();
//        outputMap.put(tfLite.getOutputIndex(outputNodes.get(scale)), outputByteBuffer);

        this.tfLite.run(input, inference);

        inference.rewind();

        return inference.asFloatBuffer().duplicate();
    }


    // Helper methods

    /**
     * Inserisce nel buffer in della pydnet l'immagine.
     *
     * @param bitmap Immagine da inserire.
     */
    private void fillInputByteBufferWithBitmap(Bitmap bitmap) {
        bitmap.getPixels(intInputPixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < bitmap.getWidth(); ++i) {
            for (int j = 0; j < bitmap.getHeight(); ++j) {
                final int val = intInputPixels[pixel++];

                //Format ARGB

                //Ogni pixel viene normalizzato e passato come float32.
                input.putFloat((((val >> 16) & 0xFF))/ 255.0f);
                input.putFloat((((val >> 8) & 0xFF))/255.0f);
                input.putFloat((((val) & 0xFF))/255.0f);

                //Se si riuscisse a quantizzare il modello, anche con gli ingressi e le uscite,
                //Allora dovrei passare degli interi: Per ora il modello quantizzato non funge:
                //Da rivedere.

//                inputByteBuffer.put((byte)((val >> 16) & 0xFF));
//                inputByteBuffer.put((byte)((val >> 8) & 0xFF));
//                inputByteBuffer.put((byte)((val) & 0xFF));
            }
        }
    }

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
}
