package it.unibo.cvlab.computescene.dataset;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SceneDataset {

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

    @Expose
    @SerializedName("cameraPose")
    private Pose cameraPose;
    @Expose
    @SerializedName("cameraDisplayPose")
    private Pose cameraDisplayPose;
    @Expose
    @SerializedName("sensorPose")
    private Pose sensorPose;

    @Expose
    @SerializedName("displayRotation")
    private int displayRotation;

    @Expose
    @SerializedName("frameNumber")
    private long frameNumber;
    @Expose
    @SerializedName("timestamp")
    private long timestmap;

    @Expose
    @SerializedName("width")
    private int width;
    @Expose
    @SerializedName("height")
    private int height;

    @Expose
    @SerializedName("numeroAncore")
    private int numeroAncore;
    @Expose
    @SerializedName("ancore")
    private Pose[] ancore;

    @Expose
    @SerializedName("nearPlane")
    private float nearPlane;
    @Expose
    @SerializedName("farPlane")
    private float farPlane;

    @Expose
    @SerializedName("viewmtx")
    private float[] viewmtx;
    @Expose
    @SerializedName("projmtx")
    private float[] projmtx;

    public SceneDataset(Pose cameraPose, Pose cameraDisplayPose, Pose sensorPose, int displayRotation, long frameNumber, long timestmap, int width, int height, int numeroAncore, Pose[] ancore, float nearPlane, float farPlane, float[] viewmtx, float[] projmtx) {
        this.cameraPose = cameraPose;
        this.cameraDisplayPose = cameraDisplayPose;
        this.sensorPose = sensorPose;
        this.displayRotation = displayRotation;
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

    public Pose getCameraPose() {
        return cameraPose;
    }

    public Pose getCameraDisplayPose() {
        return cameraDisplayPose;
    }

    public Pose getSensorPose() {
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

    public Pose[] getAncore() {
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
