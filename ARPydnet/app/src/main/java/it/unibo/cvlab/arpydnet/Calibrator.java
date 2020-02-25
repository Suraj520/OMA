package it.unibo.cvlab.arpydnet;

import android.graphics.Bitmap;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;

import java.nio.FloatBuffer;

import it.unibo.cvlab.Runner;
import it.unibo.cvlab.pydnet.Utils;

public class Calibrator {

    private final static String TAG = Calibrator.class.getSimpleName();

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.

    private static final int MAX_POINTS = 20;
    private static final int MIN_POINTS = 1;
    private static final float MIN_SCALE_FACTOR = 0.001f;
    private static final int MIN_QUANTIZER_LEVELS = 2;
    private static final int MAX_QUANTIZER_LEVELS = 256;
    private static final int MAX_QUANTIZER_MASK = 0xFF;

    public static int getNumPoints(PointCloud pointCloud){
        FloatBuffer pointsBuffer = pointCloud.getPoints();
        return pointsBuffer.remaining() / FLOATS_PER_POINT;
    }

    private static float getDistance(Pose objPose, Pose cameraPose){
        float dx = cameraPose.tx() - objPose.tx();
        float dy = cameraPose.ty() - objPose.ty();
        float dz = cameraPose.tz() - objPose.tz();

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static float getDistance(float x, float y, float z, Pose cameraPose){
        float dx = cameraPose.tx() - x;
        float dy = cameraPose.ty() - y;
        float dz = cameraPose.tz() - z;

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private float[] cameraView;
    private float[] cameraPerspective;
    private int displayRotation;
    private int surfaceWidth, surfaceHeight;

    public void setCameraView(float[] cameraView) {
        this.cameraView = cameraView;
    }

    public void setCameraPerspective(float[] cameraPerspective) {
        this.cameraPerspective = cameraPerspective;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }

    public void onSurfaceChanged(int surfaceWidth, int surfaceHeight){
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
    }

    private final float defaultScaleFactor;
    private float scaleFactor;

    public float getScaleFactor(){
        return scaleFactor;
    }

    public float minPredictedDistance = Float.MAX_VALUE, maxPredictedDistance = Float.MIN_VALUE;
    public float minDistance = Float.MAX_VALUE, maxDistance = Float.MIN_VALUE;

    public float getMinPredictedDistance() {
        return minPredictedDistance;
    }

    public float getMaxPredictedDistance() {
        return maxPredictedDistance;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    private int numVisiblePoints;

    public int getNumVisiblePoints() {
        return numVisiblePoints;
    }

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastTimestamp = 0;
    private Runner[] pool;
    private Utils.Resolution resolution;
    private int[] output;

    public Calibrator(float scaleFactor, Utils.Resolution resolution,  int poolSize){
        this.defaultScaleFactor = scaleFactor;
        this.scaleFactor = defaultScaleFactor;
        this.resolution = resolution;
        this.pool = new Runner[poolSize];

        this.output = new int[resolution.getHeight()*resolution.getWidth()];

        for (int i = 0; i < poolSize; i++) {
            pool[i] = new Runner();
            pool[i].start();
        }
    }

    public void dispose() throws InterruptedException{
        for (Runner runner : pool) {
            runner.stop();
        }
    }

    private float[] transformCoordBottomLeft(float xFloat, float yFloat){
        float tmp;
        switch (displayRotation){
            case 0:
                //Dritto: origine top-right: inverto xy e scambio
                xFloat = 1.0f - xFloat;
                yFloat = 1.0f - yFloat;

                //Scambio
                tmp = yFloat; yFloat = xFloat; xFloat = tmp;
                break;
            case 90:
                //Landscape con parte a destra sopra: origine top-left:  inverto y
                yFloat = 1.0f - yFloat;
                break;
            case 180:
                //Sottosopra: origine botton-left: scambio
                //Scambio....
                tmp = yFloat; yFloat = xFloat; xFloat = tmp;
                break;
            case 270:
                //Landscape con parte a sinistra sopra: origine bottom-right: inverto x
                xFloat = 1.0f - xFloat;
                break;
        }

        return new float[]{xFloat, yFloat};
    }

    private float[] transformCoordTopLeft(float xFloat, float yFloat){
        float tmp;

        switch (displayRotation){
            case 0:
                //Dritto: origine top-right: inverto x
                xFloat = 1.0f - xFloat;
                //Ho bisogno anche di uno scambio xy
                tmp = xFloat; xFloat = yFloat; yFloat = tmp;
                break;
            case 90:
                //Landscape con parte a destra sopra: origine top-left:  nulla
                break;
            case 180:
                //Sottosopra: origine botton-left: inverto y
                yFloat = 1.0f - yFloat;
                //Ho bisogno anche di uno scambio xy
                tmp = xFloat; xFloat = yFloat; yFloat = tmp;
                break;
            case 270:
                //Landscape con parte a sinistra sopra: origine bottom-right: inverto xy
                xFloat = 1.0f - xFloat;
                yFloat = 1.0f - yFloat;
                break;
        }

        return new float[]{xFloat, yFloat};
    }

    //https://learnopengl.com/Getting-started/Coordinate-Systems

    /**
     * Calibra lo scale factor da un solo punto di osservazione.
     * @param inference risultato della pydnet
     * @param objPose punto dell'oggetto
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     */
    public void calibrateScaleFactor(FloatBuffer inference, Pose objPose, Pose cameraPose){

        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

        //Passaggio fondamentale: trasformazione delle coordinate.
        float[] coords = new float[4];

        coords[0] = objPose.tx();
        coords[1] = objPose.ty();
        coords[2] = objPose.tz();
        coords[3] = 1.0f;

        float[] projCoords = new float[4];
        Matrix.multiplyMV(projCoords, 0, modelViewProjection,0, coords,0);

        //Passaggio alle cordinate normali
        projCoords[0] /= projCoords[3];
        projCoords[1] /= projCoords[3];

        //Clipping: se il punto è fuori dallo schermo non lo considero.
        if(projCoords[0] > 1.0f || projCoords[0] < -1.0f){
            Log.w(TAG, "Il punto di calibrazione è fuori schermo: "+projCoords[0]);
            return;
        }

        if(projCoords[1] > 1.0f || projCoords[1] < -1.0f){
            Log.w(TAG, "Il punto di calibrazione è fuori schermo: "+projCoords[1]);
            return;
        }

        //Viewport Transform
        //Coordinate iniziali: [-1.0,1.0], [-1.0,1.0]
        //Coordinate normalizzate(uv): [0.0,1.0]
        //Origine in bottom-left.
        //Seguo i fragment per la conversione.
        float xFloat = (projCoords[0] * 0.5f) + 0.5f;
        float yFloat = (projCoords[1] * 0.5f) + 0.5f;

        //Trasformo le coordinate da origine bottom-left, in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordBottomLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * resolution.getWidth());
        int y = Math.round(finalCoords[1] * resolution.getHeight());

        int position = (resolution.getWidth() * y) + x;

        inference.rewind();

        if(inference.remaining() >= position){
            float predictedDistance = inference.get(position);
            scaleFactor = getDistance(objPose, cameraPose) / predictedDistance;
        }else{
            //Stranamente non riesco a trovare la predizione.
            Log.w(TAG, "Impossibile trovare predizione di calibrazione");
            scaleFactor = defaultScaleFactor;
        }
    }

    /**
     * Calibra lo scale factor da un solo punto di osservazione.
     * @param inference risultato della pydnet
     * @param objPose punto dell'oggetto
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     * @param rawX 0 <= x < width sullo schermo
     * @param rawY 0 <= y < height sullo schermo
     * @return scale factor
     */
    public float calibrateScaleFactor(FloatBuffer inference, Pose objPose, Pose cameraPose, int rawX, int rawY){
        //Origine dei punti Top-left
        //Devo ricavare i punti relativi
        float xFloat = (float) rawX/surfaceWidth;
        float yFloat = (float) rawY/surfaceHeight;

        //Trasformo le coordinate da origine top-left, in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordTopLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * resolution.getWidth());
        int y = Math.round(finalCoords[1] * resolution.getHeight());

        int position = (resolution.getWidth() * y) + x;

        inference.rewind();

        if(inference.remaining() >= position){
            float predictedDistance = inference.get(position);
            scaleFactor = getDistance(objPose, cameraPose) / predictedDistance;
        }else{
            //Stranamente non riesco a trovare la predizione.
            Log.w(TAG, "Impossibile trovare predizione di calibrazione");
            scaleFactor = defaultScaleFactor;
        }

        return scaleFactor;
    }

    //https://www.learnopengles.com/tag/perspective-divide/
    //http://www.songho.ca/opengl/gl_transform.html

    /**
     * Cerca di trovare lo scale factor tra pydnet e arcore, tramite point cloud.
     *
     * @param inference risultato della pydnet
     * @param cloud nuovola di punti di arcore
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     */
    public void calibrateScaleFactor(FloatBuffer inference, PointCloud cloud, Pose cameraPose){
        if (cloud.getTimestamp() == lastTimestamp) {
            // Redundant call.
            return;
        }

        //Vettore con le World-Coords
        //X,Y,Z,Confidence
        float[] coords = new float[4];

        //Vettore di prima dopo la View Projection Matrix.
        //X,Y,Z,(A)
        float[] projectionCoords = new float[4];

        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

        lastTimestamp = cloud.getTimestamp();

        FloatBuffer points = cloud.getPoints();

        int numPoints = getNumPoints(cloud);
        numVisiblePoints = numPoints;

//        Log.d(TAG, "Num points: "+numPoints);

        //Ho un range di valori per cui accetto la nuvola.
        if(numPoints < MIN_POINTS) return;

        numPoints = numPoints > MAX_POINTS ? MAX_POINTS : numPoints;

        float sumScaleFactor = 0;
        float sumWeight = 0;

        float minPredictedDistance = Float.MAX_VALUE;
        float maxPredictedDistance = Float.MIN_VALUE;
        float minDistance = Float.MAX_VALUE;
        float maxDistance = Float.MIN_VALUE;

        int i = 0;

        for (; i < numPoints && points.remaining() >= FLOATS_PER_POINT; i++){
            points.get(coords,0,4);

            float weight = coords[3];
            coords[3] = 1.0f;

            //Passaggio fondamentale: trasformazione delle coordinate.
            Matrix.multiplyMV(projectionCoords, 0, modelViewProjection,0, coords,0);

            //Passaggio alle cordinate normali
            projectionCoords[0] /= projectionCoords[3];
            projectionCoords[1] /= projectionCoords[3];
//            projectionCoords[2] /= projectionCoords[3];//La z non mi interessa.

            //Clipping: se il punto è fuori dallo schermo non lo considero.
            //Avrei potuto spostare sopra il clipping e verificare con w
            if(projectionCoords[0] > 1.0f || projectionCoords[0] < -1.0f){
                i--;    //Il punto non è valido: non lo conto e prendo il successivo
                numVisiblePoints--;
                continue;
            }

            if(projectionCoords[1] > 1.0f || projectionCoords[1] < -1.0f){
                i--;    //Il punto non è valido: non lo conto e prendo il successivo
                numVisiblePoints--;
                continue;
            }

            //Viewport Transform
            //Coordinate iniziali: [-1.0,1.0], [-1.0,1.0]
            //Coordinate normalizzate(uv): [0.0,1.0]
            //Origine in bottom-left.
            //Seguo i fragment per la conversione.

            float xFloat = (projectionCoords[0] * 0.5f) + 0.5f;
            float yFloat = (projectionCoords[1] * 0.5f) + 0.5f;

            //Trasformo le coordinate da origine bottom-left, in modo che seguano la rotazione dello schermo
            float[] finalCoords = transformCoordBottomLeft(xFloat, yFloat);

            //Trasformo le coordinate normalizate in [0,width[, [0.height[
            int x = Math.round(finalCoords[0] * resolution.getWidth());
            int y = Math.round(finalCoords[1] * resolution.getHeight());

            int position = (resolution.getWidth() * y) + x;

            inference.rewind();

            if(inference.remaining() >= position){
                //Ricavo la distanza.
                float distance = getDistance(coords[0], coords[1], coords[2], cameraPose);
                //Ricavo la distanza pydnet.
                float predictedDistance = inference.get(position);
                //Faccio la somma: prendo una media PONDERATA dei punti.
                sumScaleFactor += (distance / predictedDistance )* weight;
                sumWeight += weight;

                if(minDistance > distance) minDistance = distance;
                if(maxDistance < distance) maxDistance = distance;
                if(minPredictedDistance > predictedDistance) minPredictedDistance = predictedDistance;
                if(maxPredictedDistance < predictedDistance) maxPredictedDistance = predictedDistance;
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.w(TAG, "Impossibile trovare predizione di calibrazione");
                i--;//Il punto è perso: ripeto con in prossimo.
            }

        }

        //Devo verificare che ci sia il minimo di punti per la somma.
        if(i > 0){
            float tmpScaleFactor = sumScaleFactor / sumWeight;
//            Log.d(TAG, "Calculated pydnet scale factor: "+tmpScaleFactor);
//            Log.d(TAG, "Distance (min, max): "+minDistance+", "+maxDistance);
//            Log.d(TAG, "Predicted distance (min, max): "+minPredictedDistance+", "+maxPredictedDistance);
//            Log.d(TAG, "Scaled predicted distance (min, max): "+minPredictedDistance*scaleFactor+", "+maxPredictedDistance*scaleFactor);

            if(tmpScaleFactor <= MIN_SCALE_FACTOR || Float.isNaN(tmpScaleFactor)){
                Log.d(TAG, "Invalid scale factor. Set: "+scaleFactor);
            }else{
                scaleFactor = tmpScaleFactor;
            }
        }

//        Log.d(TAG, "Visible Points: "+visiblePoints);

        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.minPredictedDistance = minPredictedDistance;
        this.maxPredictedDistance = maxPredictedDistance;

        points.rewind();
    }

    public float getQuantizerFactor(){
        return maxPredictedDistance;
    }

    /**
     * Produce una mappa quantizzata con un tot di livelli. Utile per omogeneizzare i livelli.
     *
     * @param inference previsione
     * @param numberThread numero di thread da utilizzare
     * @param levels livelli (multiplo di 2)
     * @return bitmap con la mappa quantizzata
     */
    public Bitmap getQuantizedDepthMap(final FloatBuffer inference, int numberThread, int levels) {

        //Controllo che nella pool ci siano abbastanza thread
        if(pool.length < numberThread){
            numberThread = pool.length;
        }

        //Imposto un numero di livelli minimo
        if(levels < MIN_QUANTIZER_LEVELS){
            levels = MIN_QUANTIZER_LEVELS;
        }

        //Controllo che il numero di livelli sia un multiplo di 2.
        if(levels % 2 != 0){
            levels = levels / 2;
            levels = levels * 2;
        }

        //Se il numero di livelli è troppo grande allora lo riduco.
        while(levels > MAX_QUANTIZER_LEVELS) levels = levels / 2;

        final int finalLevels = levels-1;
        final float scale = ((float)(MAX_QUANTIZER_LEVELS-1)) /((float)(finalLevels));

        inference.rewind();

        int inferenceLength = inference.remaining();
        int length = Math.round(inferenceLength / numberThread);

        for (int index = 0; index < numberThread; index++) {
            int current_start = index*length;
            int current_end = current_start + length;
            current_end = Math.min(current_end, inferenceLength);

            final int curStart = current_start;
            final int curEnd = current_end;

            pool[index].doJob(()->{
                for(int i = curStart; i < curEnd; i++){
                    float prediction = inference.get(i);

                    //Normalization
                    prediction = prediction / maxPredictedDistance;

                    //Lower Clipping
                    prediction = Math.max(prediction, 0.0f);

                    //Sfrutto la conversione float->int per effettuare la quantizzazione. (troncamento)

                    int quantizedPrediciton = (int)(prediction * (finalLevels));
                    quantizedPrediciton = (int)((float)quantizedPrediciton * scale);

                    //Upper Clipping
                    quantizedPrediciton = quantizedPrediciton & MAX_QUANTIZER_MASK;

                    //Format ARGB 8 bit per value.
                    int argbPixel = 0xFF000000;

                    argbPixel |= quantizedPrediciton << 16;
                    argbPixel |= quantizedPrediciton << 8;
                    argbPixel |= quantizedPrediciton;

                    output[i] = argbPixel;
                }
            });
        }

        try {
            for (Runner thread : pool)
                thread.waitJob();

            Bitmap bmp = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.setPixels(output, 0, resolution.getWidth(), 0, 0, resolution.getWidth(), resolution.getHeight());

            return bmp;

        }
        catch (InterruptedException e){
            Log.d(TAG, "Thread Interrotto nel join", e);
            return null;
        }

    }

    /**
     * Usato per testare che le coordinate della finestra ottenute siano equivalenti a quelle date
     *
     * @param objPose Posa dell'oggetto
     * @param rawX x test
     * @param rawY y test
     * @return Se il risultato è prossimo a zero allora il test ha successo.
     */
    public float xyTest(Pose objPose, float rawX, float rawY){
        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

        //Passaggio fondamentale: trasformazione delle coordinate.
        float[] coords = new float[4];

        coords[0] = objPose.tx();
        coords[1] = objPose.ty();
        coords[2] = objPose.tz();
        coords[3] = 1.0f;

        float[] projCoords = new float[4];
        Matrix.multiplyMV(projCoords, 0, modelViewProjection,0, coords,0);

        //Passaggio alle cordinate normali
        projCoords[0] /= projCoords[3];
        projCoords[1] /= projCoords[3];
//            projectionCoords[2] /= projectionCoords[3];//La z non mi interessa.

        //Salto il clipping.

        //Viewport Transform
        //Coordinate iniziali: [-1.0,1.0], [-1.0,1.0]
        //Non ho bisogno di trasformare le coordinate in normali: l'origine resta fissa
        //Coordinate finali: [0, width], [0, height]

        float widthFloatDiv2 = (float) surfaceWidth / 2;
        float heightFloatDiv2 = (float) surfaceHeight / 2;

        float xFloat = (projCoords[0] * widthFloatDiv2) + widthFloatDiv2;
        //Effettuo anche una inversione y: bottom-left -> top-left
        float yFloat = (-projCoords[1] * heightFloatDiv2) + heightFloatDiv2;

        int x = Math.round(xFloat);
        int y = Math.round(yFloat);

        Log.d(TAG, "XY Test XY: "+x+", "+y);

        double dx = rawX - x;
        double dy = rawY - y;

        return  (float)Math.sqrt(dx*dx+dy*dy);
    }

    /**
     * Testa il calcolo dello scale factor tramite le coordinate della finestra.
     *
     * @param inference risultato pydnet
     * @param objPose posa dell'oggetto
     * @param cameraPose posa della camera
     * @param rawX coordinata x riferita all'oggetto (origine top-left)
     * @param rawY coordinata x riferita all'oggetto (origine top-left)
     * @return Se il risultato è prossimo a zero vuol dire che lo scale factor corrisponde.
     */
    public float calibrationTest(FloatBuffer inference, Pose objPose, Pose cameraPose, float rawX, float rawY){
        //L'origine XY è Top-left
        //Per effettuare le trasformazioni ho bisogno di coordinate normali
        float xFloat = rawX/surfaceWidth;
        float yFloat = rawY/surfaceHeight;

        //Trasformo le coordinate da origine top-left, in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordTopLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * resolution.getWidth());
        int y = Math.round(finalCoords[1] * resolution.getHeight());

        Log.d(TAG, "Calibration Test XY: "+x+", "+y);

        int position = (resolution.getWidth() * y) + x;
        inference.rewind();

        //Ricavo la distanza.
        float distance = getDistance(objPose, cameraPose);
        //Ricavo la distanza pydnet.
        float predictedDistance = inference.get(position);

        float myScaleFactor = distance / predictedDistance;

        float dFactor = myScaleFactor - scaleFactor;

        return (float)Math.sqrt(dFactor*dFactor);
    }

}
