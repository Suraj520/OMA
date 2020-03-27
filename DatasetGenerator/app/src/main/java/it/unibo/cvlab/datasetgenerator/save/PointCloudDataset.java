package it.unibo.cvlab.datasetgenerator.save;

import android.opengl.Matrix;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.gson.annotations.SerializedName;

import java.nio.FloatBuffer;

public class PointCloudDataset {

    private static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.

    private static float getDistance(float x, float y, float z, Pose cameraPose){
        float dx = cameraPose.tx() - x;
        float dy = cameraPose.ty() - y;
        float dz = cameraPose.tz() - z;

        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    public static int getNumPoints(PointCloud pointCloud){
        FloatBuffer pointsBuffer = pointCloud.getPoints();
        return pointsBuffer.remaining() / FLOATS_PER_POINT;
    }

    //https://www.learnopengles.com/tag/perspective-divide/
    //http://www.songho.ca/opengl/gl_transform.html

    public static PointCloudDataset parseDataset(PointCloud pointCloud, Pose sensorPose, com.google.ar.core.Camera camera, int width, int height, float nearPlane, float farPlane){

        //Vettore con le World-Coords
        //X,Y,Z,Confidence
        float[] coords = new float[4];

        //Vettore di prima dopo la View Projection Matrix.
        //X,Y,Z,(A)
        float[] projectionCoords = new float[4];

        // Get projection matrix.
        float[] projmtx = new float[16];
        camera.getProjectionMatrix(projmtx, 0, nearPlane, farPlane);

        // Get camera matrix and draw.
        float[] viewmtx = new float[16];
        camera.getViewMatrix(viewmtx, 0);

        //Matrice usata per la trasformazione delle coordinate: world->view
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, projmtx, 0, viewmtx, 0);

        MyPose cameraData = new MyPose(camera.getPose());
        MyPose cameraDisplayData = new MyPose(camera.getDisplayOrientedPose());
        MyPose sensorData = new MyPose(sensorPose);

        FloatBuffer pointsBuffer = pointCloud.getPoints();
        int numPoints = pointsBuffer.remaining() / FLOATS_PER_POINT;

        Point[] points = new Point[numPoints];

        float widthFloatDiv2 = (float) width/ 2;
        float heightFloatDiv2 = (float) height/ 2;

        //Serve a dire quanti sono i punti visibili.
        int visiblePoints = 0;

        float minDistance = Float.MAX_VALUE;
        float maxDistance = Float.MIN_VALUE;

        //int i =0: serve per scorrere tutti i punti
        for (int i = 0; i < numPoints; i++){
            pointsBuffer.get(coords, 0, FLOATS_PER_POINT);

            float confidence = coords[3];
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
                //Il punto non è valido: non lo conto e prendo il successivo
                continue;
            }

            if(projectionCoords[1] > 1.0f || projectionCoords[1] < -1.0f){
                //Il punto non è valido: non lo conto e prendo il successivo
                continue;
            }

            //Viewport Transform
            //Faccio anche una trasformazione d'orgine da left-bottom a left-top.
            //Coordinate iniziali: [-1.0,1.0], [-1.0,1.0]
            //Coordinate finali: [0, width], [0, height]

            float xFloat = (projectionCoords[0] * widthFloatDiv2) + widthFloatDiv2;
            float yFloat = (-projectionCoords[1] * heightFloatDiv2) + heightFloatDiv2;

            int x = Math.round(xFloat);
            int y = Math.round(yFloat);

            //Posso usare direttamente coords che sono in coordinate spaziali come quelle della posa
            //della camera
            float distance = getDistance(coords[0], coords[1], coords[2], camera.getDisplayOrientedPose());

            if(distance > maxDistance) maxDistance = distance;
            if(distance < minDistance) minDistance = distance;

            points[visiblePoints++] = new Point(x, y, coords[0], coords[1], coords[2], confidence, distance);
        }

        return new PointCloudDataset(cameraData, cameraDisplayData, sensorData, pointCloud.getTimestamp(), points, minDistance, maxDistance, "left-top", numPoints, visiblePoints, width, height, nearPlane, farPlane, viewmtx, projmtx);
    }

    //Informazioni ricavate:
    //Pose della camera, della camera rispetto al display (rotazione Z)
    //Posa del sensore

    //Punti visibili nello schermo: per ogni punto x,y (window coord) x,y,z (world coord) confidence, distance

    //Origine della window coord

    //Numero punti della point cloud e numero punti visibili a schermo (di solito coincidono)

    //Dimensione della window

    //Distanza dai piani di proiezione
    //Matrici di proiezione.

    @SerializedName("cameraPose")
    private MyPose cameraPose;
    @SerializedName("cameraDisplayPose")
    private MyPose cameraDisplayPose;
    @SerializedName("sensorPose")
    private MyPose sensorPose;

    @SerializedName("displayRotation")
    private int displayRotation;
    @SerializedName("pointCloudTimestamp")
    private long pointCloudTimestamp;

    @SerializedName("visiblePoints")
    private Point[] visiblePoints;
    @SerializedName("minDistance")
    private float minDistance;
    @SerializedName("maxDistance")
    private float maxDistance;

    @SerializedName("origin")
    private String origin;

    @SerializedName("numPoints")
    private int numPoints;
    @SerializedName("numVisiblePoints")
    private int numVisiblePoints;

    @SerializedName("width")
    private int width;
    @SerializedName("height")
    private int height;

    @SerializedName("nearPlane")
    private float nearPlane;
    @SerializedName("farPlane")
    private float farPlane;

    @SerializedName("viewmtx")
    private float[] viewmtx;
    @SerializedName("projmtx")
    private float[] projmtx;

    @SerializedName("accelerometerData")
    private SensorData accelerometerData;
    @SerializedName("linearAccelerometerData")
    private SensorData linearAccelerometerData;
    @SerializedName("gyroscopeData")
    private SensorData gyroscopeData;
    @SerializedName("pose6DofData")
    private SensorData pose6DofData;

    private PointCloudDataset(MyPose cameraPose, MyPose cameraDisplayPose, MyPose sensorPose, long pointCloudTimestamp, Point[] visiblePoints, float minDistance, float maxDistance, String origin, int numPoints, int numVisiblePoints, int width, int height, float nearPlane, float farPlane, float[] viewmtx, float[] projmtx) {
        this.cameraPose = cameraPose;
        this.cameraDisplayPose = cameraDisplayPose;
        this.sensorPose = sensorPose;
        this.pointCloudTimestamp = pointCloudTimestamp;
        this.visiblePoints = visiblePoints;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.origin = origin;
        this.numPoints = numPoints;
        this.numVisiblePoints = numVisiblePoints;
        this.width = width;
        this.height = height;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.viewmtx = viewmtx;
        this.projmtx = projmtx;
    }

    public MyPose getCameraPose() {
        return cameraPose;
    }

    public MyPose getCameraDisplayPose() {
        return cameraDisplayPose;
    }

    public MyPose getSensorPose() {
        return sensorPose;
    }

    public Point[] getVisiblePoints() {
        return visiblePoints;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public String getOrigin() {
        return origin;
    }

    public int getNumPoints() {
        return numPoints;
    }

    public int getNumVisiblePoints() {
        return numVisiblePoints;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    public float[] getViewmtx() {
        return viewmtx;
    }

    public float[] getProjmtx() {
        return projmtx;
    }

    public SensorData getAccelerometerData() {
        return accelerometerData;
    }

    public SensorData getLinearAccelerometerData() {
        return linearAccelerometerData;
    }

    public SensorData getGyroscopeData() {
        return gyroscopeData;
    }

    public SensorData getPose6DofData() {
        return pose6DofData;
    }

    public int getDisplayRotation() {
        return displayRotation;
    }

    public long getPointCloudTimestamp() {
        return pointCloudTimestamp;
    }

    public void setAccelerometerData(SensorData accelerometerData) {
        this.accelerometerData = accelerometerData;
    }

    public void setLinearAccelerometerData(SensorData linearAccelerometerData) {
        this.linearAccelerometerData = linearAccelerometerData;
    }

    public void setGyroscopeData(SensorData gyroscopeData) {
        this.gyroscopeData = gyroscopeData;
    }

    public void setPose6DofData(SensorData pose6DofData) {
        this.pose6DofData = pose6DofData;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }
}
