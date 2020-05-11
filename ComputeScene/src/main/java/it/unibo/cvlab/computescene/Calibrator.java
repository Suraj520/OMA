package it.unibo.cvlab.computescene;

import android.opengl.Matrix;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.cvlab.computescene.dataset.ITraslation;
import it.unibo.cvlab.computescene.dataset.Point;
import it.unibo.cvlab.computescene.dataset.Pose;

public class Calibrator {

    private final static String TAG = Calibrator.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(Calibrator.class.getSimpleName());

    private static final float MAPPER_SCALE_FACTOR = 0.2f;

    Random rnd = new Random();

    private int width, height;
    private Pose cameraPose;
    private float[] cameraView;
    private float[] cameraPerspective;
    private int displayRotation;

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setCameraPose(Pose cameraPose) {
        this.cameraPose = cameraPose;
    }

    public void setCameraView(float[] cameraView) {
        this.cameraView = cameraView;
    }

    public void setCameraPerspective(float[] cameraPerspective) {
        this.cameraPerspective = cameraPerspective;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }

    private int numVisiblePoints;

    public int getNumVisiblePoints() {
        return numVisiblePoints;
    }

    private final double defaultScaleFactor;
    private double scaleFactor;

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public double getScaleFactor(){
        return scaleFactor;
    }

    public Calibrator() {
        this(MAPPER_SCALE_FACTOR);
    }

    public Calibrator(double scaleFactor){
        this.defaultScaleFactor = scaleFactor;
        this.scaleFactor = defaultScaleFactor;
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

    private float getDistance(Point point){
        float dx = cameraPose.getTx() - point.getTx();
        float dy = cameraPose.getTy() - point.getTy();
        float dz = cameraPose.getTz() - point.getTz();

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private float getDistance(float x, float y, float z){
        float dx = cameraPose.getTx() - x;
        float dy = cameraPose.getTy() - y;
        float dz = cameraPose.getTz() - z;

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    /**
     * Effettua le trasformazioni OpenGL per ricavare le coordinate XY di un punto nelle coordinate world-space.
     * @param point punto nelle coordinate world-space
     * @return int[] array con le coordinate XY (X:0, Y:1), NULL nel caso di punto fuori schermo
     */
    public int[] getXYFromPoint(ITraslation point){
        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] viewProjectionMatrix = new float[16];
        Matrix.multiplyMM(viewProjectionMatrix, 0, cameraPerspective, 0, cameraView, 0);

        float[] coords = new float[4];
        float[] projCoords = new float[4];

        //Estrazione coordinate dal punto
        coords[0] = point.getTx();
        coords[1] = point.getTy();
        coords[2] = point.getTz();
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

        //Trasformo le coordinate da origine bottom-left, in modo che seguano la rotazione dello schermo
        float[] finalCoords = transformCoordBottomLeft(xFloat, yFloat);

        //Trasformo le coordinate normalizate in [0,width[, [0.height[
        int x = Math.round(finalCoords[0] * (width-1));
        int y = Math.round(finalCoords[1] * (height-1));

        return new int[]{x,y};
    }

    //https://learnopengl.com/Getting-started/Coordinate-Systems

    /**
     * Calibra lo scale factor da un solo punto di osservazione.
     * @param inference risultato della pydnet
     * @param points lista dei punti dell'oggetto
     */
    public boolean calibrateScaleFactor(FloatBuffer inference, Point[] points){
        double sumScaleFactor = 0.0;
        double sumWeight = 0.0;

        numVisiblePoints = 0;

        for(Point point : points){
            if(point == null) continue;

            int[] coords = getXYFromPoint(point);

            if(coords == null){
                //Punto non valido continuo con il prossimo
                continue;
            }

            int position = (width * coords[1]) + coords[0];

            inference.rewind();

            if(inference.remaining() >= position){
                float predictedDistance = inference.get(position);
                sumScaleFactor += (getDistance(point) / predictedDistance) * point.getConfidence();
                sumWeight += point.getConfidence();

                numVisiblePoints++;
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.log(Level.INFO, "Impossibile trovare predizione di calibrazione");
            }
        }

        if (!Double.isNaN(sumScaleFactor)) {
            scaleFactor = sumScaleFactor / sumWeight;
            Log.log(Level.INFO, "Scale factor: "+scaleFactor);
            return true;
        } else {
            Log.log(Level.INFO, "Invalid scale factor. Ignored");
            return false;
        }

    }

    /**
     * Classe wrapper per un punto RANSAC.
     */
    private static class RansacObject {
        double distance;
        float predictedDistance;
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
     * @param numberOfIterations numero di iterazioni dell'algoritmo RANSAC
     * @param normalizedThreshold valore di soglia tra 0.0 e 1.0
     * @return fattore di scala trovato tramite RANSAC
     */
    public boolean calibrateScaleFactorRANSAC(FloatBuffer inference, Point[] points, int numberOfIterations, float normalizedThreshold){
        numVisiblePoints = 0;

        int numPoints = points.length;

        //Check sul numero minimo di punti
        if(numPoints < 1) return false;

        RansacObject[] ransacObjects = new RansacObject[numPoints];

        //Calcolo di ogni singolo scaleFactor O(N)
        for (Point point : points){
            if(point == null) continue;

            if(point.getConfidence() < 0.3f) continue;

            int[] coords = getXYFromPoint(point);

            if(coords == null){
                //Punto non valido continuo con il prossimo
                continue;
            }

            int position = (width * coords[1]) + coords[0];

            inference.rewind();

            if(inference.remaining() >= position){
                //Ricavo la distanza.
                double distance = getDistance(point);

                //Ricavo la distanza pydnet.
                float predictedDistance = inference.get(position);

                ransacObjects[numVisiblePoints] = new RansacObject();
                ransacObjects[numVisiblePoints].distance = distance;
                ransacObjects[numVisiblePoints].predictedDistance = predictedDistance;
                ransacObjects[numVisiblePoints].scaleFactor = (distance / predictedDistance );
                ransacObjects[numVisiblePoints].arConfidence = point.getConfidence();
                numVisiblePoints++;
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.log(Level.INFO, "Impossibile trovare predizione di calibrazione");
            }
        }

        //Numero di punti non sufficiente
        if(numVisiblePoints < 1) return false;

        //Sorting per confidenza, decrescente: O(N*log2(N))
        //Non ho scelto parallel perché la lista degli elementi è limitata
        Arrays.sort(ransacObjects, RansacObject.getComparatorByConfidenceInverse());

        //Sorting usato solo per applicare lo sbarramento
        //Applico sbarramento numero passimo punti
        //if(numVisiblePoints > MAX_POINTS) numVisiblePoints = MAX_POINTS;

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
        if(Double.isNaN(migliorScaleFactor)){
            Log.log(Level.INFO, "Invalid scale factor. Set: "+scaleFactor);
            return false;
        }else{
            scaleFactor = migliorScaleFactorAverage;
            return true;
        }
    }


}
