package it.unibo.cvlab.computescene;

import android.opengl.Matrix;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.cvlab.computescene.dataset.Point;
import it.unibo.cvlab.computescene.dataset.Pose;

public class Calibrator {

    private final static String TAG = Calibrator.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(Calibrator.class.getSimpleName());

    private static final float MIN_SCALE_FACTOR = 0.001f;
    private static final float MAPPER_SCALE_FACTOR = 0.2f;

    private static float getDistance(Point point, Pose cameraPose){
        float dx = cameraPose.getTx() - point.getTx();
        float dy = cameraPose.getTy() - point.getTy();
        float dz = cameraPose.getTz() - point.getTz();

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static float getDistance(float x, float y, float z, Pose cameraPose){
        float dx = cameraPose.getTx() - x;
        float dy = cameraPose.getTy() - y;
        float dz = cameraPose.getTz() - z;

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private float[] cameraView;
    private float[] cameraPerspective;
    private int displayRotation;

    public void setCameraView(float[] cameraView) {
        this.cameraView = cameraView;
    }

    public void setCameraPerspective(float[] cameraPerspective) {
        this.cameraPerspective = cameraPerspective;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
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

    public Calibrator() {
        this(MAPPER_SCALE_FACTOR);
    }

    public Calibrator(float scaleFactor){
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

    //https://learnopengl.com/Getting-started/Coordinate-Systems

    /**
     * Calibra lo scale factor da un solo punto di osservazione.
     * @param inference risultato della pydnet
     * @param points lista dei punti dell'oggetto
     * @param cameraPose Punto della camera in riferimento alla nuvola di punti
     */
    public void calibrateScaleFactor(FloatBuffer inference, int width, int height, Point[] points, Pose cameraPose){

        float sumScaleFactor = 0.0f;
        float sumWeight = 0.0f;

        for(Point point : points){
            if(point == null) continue;

            //Matrice usata per la trasformazione delle coordinate: world->view
            float[] viewProjectionMatrix = new float[16];
            Matrix.multiplyMM(viewProjectionMatrix, 0, cameraPerspective, 0, cameraView, 0);

            //Passaggio fondamentale: trasformazione delle coordinate.
            float[] coords = new float[4];

            coords[0] = point.getTx();
            coords[1] = point.getTy();
            coords[2] = point.getTz();
            coords[3] = 1.0f;

            float[] projCoords = new float[4];
            Matrix.multiplyMV(projCoords, 0, viewProjectionMatrix,0, coords,0);

            //Passaggio alle cordinate normali
            projCoords[0] /= projCoords[3];
            projCoords[1] /= projCoords[3];

            //Clipping: se il punto è fuori dallo schermo non lo considero.
            if(projCoords[0] > 1.0f || projCoords[0] < -1.0f){
                //Log.log(Level.WARNING, "Il punto di calibrazione è fuori schermo: "+projCoords[0]);
                continue;
            }

            if(projCoords[1] > 1.0f || projCoords[1] < -1.0f){
                //Log.log(Level.WARNING, "Il punto di calibrazione è fuori schermo: "+projCoords[1]);
                continue;
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
            int x = Math.round(finalCoords[0] * width);
            int y = Math.round(finalCoords[1] * height);

            int position = (width * y) + x;

            inference.rewind();

            if(inference.remaining() >= position){
                float predictedDistance = inference.get(position);
                sumScaleFactor += (getDistance(point, cameraPose) / predictedDistance) * point.getConfidence();
                sumWeight += point.getConfidence();
            }else{
                //Stranamente non riesco a trovare la predizione.
                Log.log(Level.WARNING, "Impossibile trovare predizione di calibrazione");
            }
        }

        if (!Float.isNaN(sumScaleFactor)) {
            scaleFactor = sumScaleFactor / sumWeight;
            Log.log(Level.INFO, "Scale factor: "+scaleFactor);
        } else {
            Log.log(Level.INFO, "Invalid scale factor. Ignored");
        }
    }


}
