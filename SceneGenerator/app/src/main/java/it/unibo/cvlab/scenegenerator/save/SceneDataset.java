package it.unibo.cvlab.scenegenerator.save;

import android.opengl.Matrix;

import com.google.ar.core.Pose;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.List;

import it.unibo.cvlab.scenegenerator.CustomAnchor;

public class SceneDataset {

    public static SceneDataset parseDataset(Pose sensorPose, com.google.ar.core.Camera camera, float[] viewmtx, float[] projmtx, List<CustomAnchor> listaAncore, final long frameNumber, final long timestamp, final int width, final int height, final float nearPlane, final float farPlane) {
        MyPose cameraData = new MyPose(camera.getPose());
        MyPose cameraDisplayData = new MyPose(camera.getDisplayOrientedPose());
        MyPose sensorData = new MyPose(sensorPose);

        MyPose[] myPoses = new MyPose[listaAncore.size()];

        int i = 0;
        for (CustomAnchor anchor : listaAncore) {
            Pose pose = anchor.getAnchor().getPose();
            myPoses[i++] = new MyPose(pose);
        }

        return new SceneDataset(cameraData, cameraDisplayData, sensorData, frameNumber, timestamp, width, height, myPoses.length, myPoses, nearPlane, farPlane, Arrays.copyOf(viewmtx, viewmtx.length), Arrays.copyOf(projmtx, projmtx.length));
    }

    //Informazioni ricavate:
    //Pose della camera, della camera rispetto al display (rotazione Z)
    //Posa del sensore

    //Rotazione dello schermo

    //Timestamp

    //Origine della window coord
    //Dimensione della window

    //Numero di ancore
    //Ancore

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

    @SerializedName("frameNumber")
    private long frameNumber;
    @SerializedName("timestamp")
    private long timestmap;

    @SerializedName("width")
    private int width;
    @SerializedName("height")
    private int height;

    @SerializedName("numeroAncore")
    private int numeroAncore;
    @SerializedName("ancore")
    private MyPose[] ancore;

    @SerializedName("nearPlane")
    private float nearPlane;
    @SerializedName("farPlane")
    private float farPlane;

    @SerializedName("viewmtx")
    private float[] viewmtx;
    @SerializedName("projmtx")
    private float[] projmtx;

    public SceneDataset(MyPose cameraPose, MyPose cameraDisplayPose, MyPose sensorPose, long frameNumber, long timestmap, int width, int height, int numeroAncore, MyPose[] ancore, float nearPlane, float farPlane, float[] viewmtx, float[] projmtx) {
        this.cameraPose = cameraPose;
        this.cameraDisplayPose = cameraDisplayPose;
        this.sensorPose = sensorPose;
        this.frameNumber = frameNumber;
        this.timestmap = timestmap;
        this.width = width;
        this.height = height;
        this.numeroAncore = numeroAncore;
        this.ancore = ancore;
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

    public int getDisplayRotation() {
        return displayRotation;
    }

    public long getFrameNumber() {
        return frameNumber;
    }

    public long getTimestmap() {
        return timestmap;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getNumeroAncore() {
        return numeroAncore;
    }

    public MyPose[] getAncore() {
        return ancore;
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

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }
}
