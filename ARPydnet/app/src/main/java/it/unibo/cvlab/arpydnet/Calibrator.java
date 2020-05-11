package it.unibo.cvlab.arpydnet;

import android.graphics.Bitmap;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import it.unibo.cvlab.Runner;
import it.unibo.cvlab.pydnet.Utils;

public class Calibrator {

    private final static String TAG = Calibrator.class.getSimpleName();

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.

    private static final int MAX_POINTS = 250;
    private static final int MIN_POINTS = 1;
    private static final int MIN_QUANTIZER_LEVELS = 2;
    private static final int MAX_QUANTIZER_LEVELS = 256;
    private static final int MAX_QUANTIZER_MASK = 0xFF;

    private static int getNumPoints(PointCloud pointCloud){
        FloatBuffer pointsBuffer = pointCloud.getPoints();
        return pointsBuffer.remaining() / FLOATS_PER_POINT;
    }

    private static double getDistance(Pose objPose, Pose cameraPose){
        float dx = cameraPose.tx() - objPose.tx();
        float dy = cameraPose.ty() - objPose.ty();
        float dz = cameraPose.tz() - objPose.tz();

        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static double getDistance(float x, float y, float z, Pose cameraPose){
        float dx = cameraPose.tx() - x;
        float dy = cameraPose.ty() - y;
        float dz = cameraPose.tz() - z;

        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private float[] cameraView;
    private float[] cameraPerspective;
    private int displayRotation;
    private int surfaceWidth, surfaceHeight;

    private Random rnd = new Random();

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
    private double scaleFactor;

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public double getScaleFactor(){
        return scaleFactor;
    }

    private double bestMSE;

    public double getBestMSE() {
        return bestMSE;
    }

    private float maxPredictedDistance;

    private int numVisiblePoints;

    public int getNumVisiblePoints() {
        return numVisiblePoints;
    }

    public float getMaxPredictedDistance() {
        return maxPredictedDistance;
    }


    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastTimestamp = 0;
    private Runner[] pool;
    private Utils.Resolution resolution;
    private int[] output;

    public Calibrator(float scaleFactor, Utils.Resolution resolution, int poolSize){
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

    /**
     * Effettua le trasformazioni OpenGL per ricavare le coordinate XY di un punto nelle coordinate
     * world-space.
     * @param x coordinata world-space X del punto
     * @param y coordinata world-space Y del punto
     * @param z coordinata world-space Z del punto
     * @return int[] array con le coordinate XY (X:0, Y:1), NULL nel caso di punto fuori schermo
     */
    private int[] getXYFromPoint(float x, float y, float z){
        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] viewProjectionMatrix = new float[16];
        Matrix.multiplyMM(viewProjectionMatrix, 0, cameraPerspective, 0, cameraView, 0);

        float[] coords = new float[4];
        float[] projCoords = new float[4];

        //Estrazione coordinate dal punto
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;
        coords[3] = 1.0f;

        //Passaggio fondamentale: trasformazione delle coordinate.
        Matrix.multiplyMV(projCoords, 0, viewProjectionMatrix,0, coords,0);

        //Passaggio alle cordinate normali
        projCoords[0] /= projCoords[3];
        projCoords[1] /= projCoords[3];

        //Clipping: se il punto è fuori dallo schermo non lo considero.
        if(projCoords[0] > 1.0f || projCoords[0] < -1.0f){
            //Log.log(Level.WARNING, "Il punto di calibrazione è fuori schermo: "+projCoords[0]);
            return null;
        }

        if(projCoords[1] > 1.0f || projCoords[1] < -1.0f){
            //Log.log(Level.WARNING, "Il punto di calibrazione è fuori schermo: "+projCoords[1]);
            return null;
        }

        //Viewport Transform
        //Coordinate iniziali: [-1.0,1.0], [-1.0,1.0]
        //Coordinate normalizzate(uv): [0.0,1.0]
        //Origine in bottom-left.
        //Seguo i fragment per la conversione.
        float xFloat = (projCoords[0] * 0.5f) + 0.5f;
        float yFloat = (projCoords[1] * 0.5f) + 0.5f;

        //Trasformo le coordinate da origine bottom-left,
        //  in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordBottomLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int xReturn = Math.round(finalCoords[0] * (resolution.getWidth()-1));
        int yReturn = Math.round(finalCoords[1] * (resolution.getHeight()-1));

        return new int[]{xReturn, yReturn};
    }

    /**
     * Cerca il massimo di una stima. Controlla solo metà dei valori, sfruttando la continuitò
     * della stima.
     * @param inference stima da verificare
     */
    public float calculateMaxEstimationDistance(FloatBuffer inference){
        //Data la continuitò della stima, posso cercare solo nei numeri pari o dispari

        float myMaxPredictedDistance = Float.MIN_VALUE;

        inference.rewind();

        while(inference.hasRemaining()){
            float predictedDistance = inference.get();

            //Ne salto uno: anche se non è il massimo si avvicina
            if(inference.hasRemaining())
                predictedDistance = inference.get();

            if(predictedDistance > myMaxPredictedDistance)
                myMaxPredictedDistance = predictedDistance;
        }

        maxPredictedDistance = myMaxPredictedDistance;

        inference.rewind();

        return maxPredictedDistance;
    }

    /**
     * Metodo per arrotondare lo scale factor, per ridurre l'effetto flickering.
     * Arrotondo alla seconda cifra decimale utile.
     * Con la prima cifra è molto stabile, ma non è preciso.
     */
    public void roundScaleFactor(){
        float multiplier = 1.0f;

        while(scaleFactor * multiplier < 10.0f){
            multiplier *= 10.0f;
        }

        scaleFactor = Math.round(scaleFactor * multiplier) / multiplier;
    }

    //https://learnopengl.com/Getting-started/Coordinate-Systems

    /**
     * Calibra lo scale factor da un solo punto di osservazione.
     * @param inference risultato della pydnet
     * @param objPose punto dell'oggetto
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     * @return restituisce lo scaleFactor: se lo scale factor non è calcolabile, restituisce
     * quello precedente.
     */
    public double calibrateScaleFactor(FloatBuffer inference, Pose objPose, Pose cameraPose){
        int[] xy = getXYFromPoint(objPose.tx(), objPose.ty(), objPose.tz());

        if(xy == null){
            //punto non valido
            return scaleFactor;
        }

        int position = (resolution.getWidth() * xy[1]) + xy[0];

        inference.rewind();

        if(inference.remaining() >= position){
            float predictedDistance = inference.get(position);


            double myScaleFactor = getDistance(objPose, cameraPose) / predictedDistance;

            if(Double.isNaN(myScaleFactor)){
                Log.d(TAG, "Invalid scale factor. Set: "+scaleFactor);
            }else{
                scaleFactor = myScaleFactor;
            }
        }else{
            //Stranamente non riesco a trovare la predizione.
            Log.d(TAG, "Impossibile trovare predizione di calibrazione");
        }

        return scaleFactor;
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
    public double calibrateScaleFactor(FloatBuffer inference, Pose objPose, Pose cameraPose,
                                       int rawX, int rawY){
        //Origine dei punti Top-left
        //Devo ricavare i punti relativi
        float xFloat = (float) rawX/surfaceWidth;
        float yFloat = (float) rawY/surfaceHeight;

        //Trasformo le coordinate da origine top-left,
        // in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordTopLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * (resolution.getWidth()-1));
        int y = Math.round(finalCoords[1] * (resolution.getHeight()-1));

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

    /**
     * Classe wrapper per un punto RANSAC.
     */
    private static class RansacObject {
        double distance;
        double predictedDistance;
        double scaleFactor;
        float arConfidence;
        double mse;

        static Comparator<RansacObject> getComparatorByConfidence(){
            return (a, b) -> {
                //Null check : https://stackoverflow.com/questions/14514467/sorting-array-with-null-values
                if (a == null && b == null) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }

                return Float.compare(a.arConfidence, b.arConfidence);
            };
        }

        static Comparator<RansacObject> getComparatorByError(){
            return (a, b) -> {
                //Null check : https://stackoverflow.com/questions/14514467/sorting-array-with-null-values
                if (a == null && b == null) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }

                return Double.compare(a.mse, b.mse);
            };
        }

        static Comparator<RansacObject> getComparatorByConfidenceInverse(){
            return (a, b) -> {
                //Null check : https://stackoverflow.com/questions/14514467/sorting-array-with-null-values
                if (a == null && b == null) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }

                return Float.compare(b.arConfidence, a.arConfidence);
            };
        }

        static Comparator<RansacObject> getComparatorByErrorInverse(){
            return (a, b) -> {
                //Null check : https://stackoverflow.com/questions/14514467/sorting-array-with-null-values
                if (a == null && b == null) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }

                return Double.compare(b.mse, a.mse);
            };
        }
    }

    /**
     * Calcola il fattore di scala tramite algoritmo RANSAC.
     * Dovrebbe essere più robusto rispetto al calcolo tramite media
     *
     * @param inference stima di profondità
     * @param cloud point cloud da cui calcolare il fattore di scala
     * @param cameraPose posa della camera per il calcolo della distanza
     * @param numberOfIterations numero di iterazioni dell'algoritmo RANSAC
     * @param normalizedThreshold valore di soglia per accettare nuovi dati nel consensusSet
     * @return fattore di scala trovato tramite RANSAC
     */
    public double calibrateScaleFactorRANSAC(FloatBuffer inference, PointCloud cloud,
                                             Pose cameraPose, int numberOfIterations,
                                             float normalizedThreshold){
        lastTimestamp = cloud.getTimestamp();

        FloatBuffer points = cloud.getPoints();
        int numPoints = getNumPoints(cloud);

        //Check sul numero minimo di punti
        if(numPoints < 1) return scaleFactor;

        RansacObject[] ransacObjects = new RansacObject[numPoints];

        //Vettore con le World-Coords
        //X,Y,Z,Confidence
        float[] coords = new float[4];

        //Calcolo di ogni singolo scaleFactor O(N)
        for (numVisiblePoints = 0; numVisiblePoints < numPoints && points.remaining() >= FLOATS_PER_POINT; numVisiblePoints++){
            points.get(coords,0,4);
            float arConfidence = coords[3];

            int[] xy = getXYFromPoint(coords[0], coords[1], coords[2]);

            if(xy == null){
                numVisiblePoints--;    //Il punto non è valido: non lo conto e prendo il successivo
                continue;
            }

            int position = (resolution.getWidth() * xy[1]) + xy[0];

            inference.rewind();

            if(inference.remaining() >= position){
                //Ricavo la distanza.
                double distance = getDistance(coords[0], coords[1], coords[2], cameraPose);

                //Ricavo la distanza pydnet.
                double predictedDistance = inference.get(position);
                predictedDistance = predictedDistance / maxPredictedDistance;
                predictedDistance = (float) (1.0 / predictedDistance);
                //predictedDistance = 255.0f - predictedDistance;

                ransacObjects[numVisiblePoints] = new RansacObject();
                ransacObjects[numVisiblePoints].distance = distance;
                ransacObjects[numVisiblePoints].predictedDistance = predictedDistance;
                ransacObjects[numVisiblePoints].scaleFactor = (distance / predictedDistance );
                ransacObjects[numVisiblePoints].arConfidence = arConfidence;
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.d(TAG, "Impossibile trovare predizione di calibrazione");
                //Il punto è perso: ripeto con il prossimo.
                numVisiblePoints--;
            }
        }

        points.rewind();

        //Check sul numero minimo di punti
        if(numVisiblePoints < 1) return scaleFactor;

        //Sorting per confidenza, decrescente: O(N*log2(N))
        //Non ho scelto parallel perché la lista degli elementi è limitata
        Arrays.sort(ransacObjects, RansacObject.getComparatorByConfidenceInverse());

        //Sorting usato solo per applicare lo sbarramento
        //Applico sbarramento numero passimo punti
        if(numVisiblePoints > MAX_POINTS) numVisiblePoints = MAX_POINTS;

        //Algoritmo RANSAC
        //Basato sullo pseudo-codice di https://it.wikipedia.org/wiki/RANSAC
        double migliorScaleFactor = Double.NaN;         //ScaleFactor stimato da punto iniziale
        double migliorScaleFactorAverage = Double.NaN;  //ScaleFactor stimato da migliorConsensusSet
        Integer[] migliorConsensusSet = new Integer[0]; //Dati che rappresentano i migliori punti

        //Inizializzazione

        //Punti contenuti in possibiliInlier
        int puntiPerPossibiliInlier = 1;

        //Punti per consensusSet: range [puntiPerPossibiliInlier, numVisiblePoints]
        int puntiConensusSet;

        for (int i = 0; i < numberOfIterations; i++){
            //possibiliInlier: Punti scelti a caso dal dataset
            Integer[] possibiliInlier = new Integer[puntiPerPossibiliInlier];
            //Ordino possibiliInlier: mi servirà dopo per scorrere i punti non presenti più velocemente
            //Uso un set perché il rnd potrebbe darmi indici uguali
            SortedSet<Integer> possibiliInlierSet = new TreeSet<>();
            while(possibiliInlierSet.size() < puntiPerPossibiliInlier){
                possibiliInlierSet.add(rnd.nextInt(numVisiblePoints));
            }
            possibiliInlier = possibiliInlierSet.toArray(possibiliInlier);

            //Possibile scale factor stimato da possibiliInlier
            double possibileScaleFactor;    //Candidato a diventare migliorScaleFactor
            double sumScaleFactors = 0.0;   //Somma degli scale factor già pesati
            double sumConfidence = 0.0;     //Somma dei pesi
            for (int k = 0; k < puntiPerPossibiliInlier; k++){
                RansacObject nextObj = ransacObjects[possibiliInlier[k]];
                sumScaleFactors += nextObj.scaleFactor * nextObj.arConfidence;
                sumConfidence += nextObj.arConfidence;
            }
            possibileScaleFactor = sumScaleFactors / sumConfidence;

            //Calcolo minimo e massimo per normalizzare

            double minError = Double.MAX_VALUE;
            double maxError = Double.MIN_VALUE;

            for (int k = 0, j=0; k < numVisiblePoints; k++){
                //Qui serve il sorting di possibiliInlier
                if(j < puntiPerPossibiliInlier && k == possibiliInlier[j]){
                    //Punto presente in possibiliInlier: skip
                    j++;
                    continue;
                }

                //Punto non presente: calcolo l'errore quadratico minimo
                RansacObject foreignObj = ransacObjects[k];

                double thisError = Math.pow(foreignObj.predictedDistance * possibileScaleFactor - foreignObj.distance, 2);

                if(thisError > maxError) maxError = thisError;
                if(thisError < minError) minError = thisError;
            }

            //consensusSet: Candidato a diventare migliorConsensusSet
            //ConsensusSet inizializzato a possibiliInlier
            Integer[] consensusSet = Arrays.copyOf(possibiliInlier, numVisiblePoints);
            puntiConensusSet = puntiPerPossibiliInlier;

            //Ricerca dei punti non assegnati ai possibili inlier che posso aggiungere al consensusSet
            for (int k = 0, j = 0; k < numVisiblePoints; k++){
                //Qui serve il sorting di possibiliInlier
                if(j < puntiPerPossibiliInlier && k == possibiliInlier[j]){
                    //Punto presente in possibiliInlier: skip
                    j++;
                    continue;
                }

                //Punto non presente: calcolo l'errore quadratico
                RansacObject foreignObj = ransacObjects[k];

                //Differenze tra scale factor
                //double squareError = Math.pow(possibileScaleFactor - foreignObj.scaleFactor, 2);
                //Differenze tra distanza predetta scalata e distanza arcore
                double thisError = Math.pow(foreignObj.predictedDistance * possibileScaleFactor - foreignObj.distance, 2);

                double normalizedError = (thisError - minError) / (maxError-minError);

                //Se l'errore è minore del threshold aggiungo il punto
                if(normalizedError < normalizedThreshold){
                    consensusSet[puntiConensusSet++] = k;

                    //Aggiorno le somme, MA NON maxSquareError:
                    sumScaleFactors += foreignObj.scaleFactor * foreignObj.arConfidence;
                    sumConfidence += foreignObj.arConfidence;
                }
            }

            //Se ho un numero sufficiente di punti allora il modello è buono
            if(puntiConensusSet >= migliorConsensusSet.length){
                //Ricalcolo possibleScaleFactor
                migliorScaleFactorAverage = sumScaleFactors / sumConfidence;
                migliorScaleFactor = possibileScaleFactor;
                migliorConsensusSet = Arrays.copyOfRange(consensusSet, 0, puntiConensusSet);
            }
        }

        //Salvo il nuovo valore.
        if(Double.isNaN(migliorScaleFactorAverage)){
            Log.d(TAG, "Invalid scale factor. Set: "+scaleFactor);
        }else{
            scaleFactor = migliorScaleFactorAverage;
        }

        return scaleFactor;
    }

    //https://www.learnopengles.com/tag/perspective-divide/
    //http://www.songho.ca/opengl/gl_transform.html

    /**
     * Cerca di trovare lo scale factor tra pydnet e arcore, tramite point cloud.
     * Uso una media ponderata per il calcolo.
     * Dipende dall'affidabilità della stima.
     *
     * @param inference risultato della pydnet
     * @param cloud nuovola di punti di arcore
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     */
    public double calibrateScaleFactor(FloatBuffer inference, PointCloud cloud, Pose cameraPose){
        //Vettore con le World-Coords
        //X,Y,Z,Confidence
        float[] coords = new float[4];

        lastTimestamp = cloud.getTimestamp();

        FloatBuffer points = cloud.getPoints();

        int numPoints = getNumPoints(cloud);
        numVisiblePoints = numPoints;

        //Ho un range di valori per cui accetto la nuvola.
        if(numPoints < MIN_POINTS) return scaleFactor;

        numPoints = Math.min(numPoints, MAX_POINTS);

        float sumScaleFactor = 0;
        float sumWeight = 0;

        for (numVisiblePoints = 0; numVisiblePoints < numPoints && points.remaining() >= FLOATS_PER_POINT; numVisiblePoints++){
            points.get(coords,0,4);
            float weight = coords[3];

            int[] xy = getXYFromPoint(coords[0], coords[1], coords[2]);

            if(xy == null){
                numVisiblePoints--;    //Il punto non è valido: non lo conto e prendo il successivo
                continue;
            }

            int position = (resolution.getWidth() * xy[1]) + xy[0];

            inference.rewind();

            if(inference.remaining() >= position){
                //Ricavo la distanza.
                double distance = getDistance(coords[0], coords[1], coords[2], cameraPose);

                //Ricavo la distanza pydnet.
                float predictedDistance = inference.get(position);
                predictedDistance = predictedDistance / maxPredictedDistance;
                predictedDistance = (float) (1.0 / predictedDistance);
                //predictedDistance = 255.0f - predictedDistance;

                //Faccio la somma: prendo una media PONDERATA dei punti.
                sumScaleFactor += (distance / predictedDistance ) * weight;
                sumWeight += weight;
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.d(TAG, "Impossibile trovare predizione di calibrazione");
                numVisiblePoints--;//Il punto è perso: ripeto con il prossimo.
            }
        }

        //Devo verificare che ci sia il minimo di punti per la somma.
        if(sumWeight > 0){
            float tmpScaleFactor = sumScaleFactor / sumWeight;

            if(Float.isNaN(tmpScaleFactor)){
                Log.d(TAG, "Invalid scale factor. Set: "+scaleFactor);
            }else{
                scaleFactor = tmpScaleFactor;
            }
        }

        points.rewind();

        return scaleFactor;
    }

    /**
     * Verifica che il point cloud sia aggiornato
     * @param cloud point cloud di riferimento della scena
     * @return vero se il timestamp del point cloud è cambiato
     */
    public boolean isLastTimestampDifferent(PointCloud cloud){
        return cloud.getTimestamp() != lastTimestamp;
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
        int length = Math.round((float)inferenceLength / numberThread);

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

                    //Sfrutto il cast per effettuare la quantizzazione. (troncamento)

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

            Bitmap bmp = Bitmap.createBitmap(resolution.getWidth(),
                    resolution.getHeight(), Bitmap.Config.ARGB_8888);

            bmp.setPixels(output, 0, resolution.getWidth(),
                    0, 0, resolution.getWidth(), resolution.getHeight());

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
    public double xyTest(Pose objPose, float rawX, float rawY){
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

        return  Math.sqrt(dx*dx+dy*dy);
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
    public double calibrationTest(FloatBuffer inference, Pose objPose,
                                  Pose cameraPose, float rawX, float rawY){
        //L'origine XY è Top-left
        //Per effettuare le trasformazioni ho bisogno di coordinate normali
        float xFloat = rawX/surfaceWidth;
        float yFloat = rawY/surfaceHeight;

        //Trasformo le coordinate da origine top-left,
        //  in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordTopLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * (resolution.getWidth()-1));
        int y = Math.round(finalCoords[1] * (resolution.getHeight()-1));

        Log.d(TAG, "Calibration Test XY: "+x+", "+y);

        int position = (resolution.getWidth() * y) + x;
        inference.rewind();

        //Ricavo la distanza.
        double distance = getDistance(objPose, cameraPose);
        //Ricavo la distanza pydnet.
        float predictedDistance = inference.get(position);

        double myScaleFactor = distance / predictedDistance;

        double dFactor = myScaleFactor - scaleFactor;

        return Math.sqrt(dFactor*dFactor);
    }

}
