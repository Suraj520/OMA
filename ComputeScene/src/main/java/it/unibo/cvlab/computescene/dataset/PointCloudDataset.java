package it.unibo.cvlab.computescene.dataset;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.nio.FloatBuffer;

public class PointCloudDataset {

    //Informazioni ricavate:
    //Pose della camera, della camera rispetto al display (rotazione Z)
    //Posa del sensore

    //Punti visibili nello schermo: per ogni punto x,y (window coord) x,y,z (world coord) confidence, distance

    //Origine della window coord

    //Numero punti della point cloud e numero punti visibili a schermo (di solito coincidono)

    //Dimensione della window

    //Distanza dai piani di proiezione
    //Matrici di proiezione.

    @Expose
    @SerializedName("pointCloudTimestamp")
    private long pointCloudTimestamp;

    @Expose
    @SerializedName("minDistance")
    private float minDistance;
    @Expose
    @SerializedName("maxDistance")
    private float maxDistance;

    @Expose
    @SerializedName("origin")
    private String origin;

    @Expose
    @SerializedName("numPoints")
    private int numPoints;

    @Expose
    @SerializedName("points")
    private Point[] points;

    public PointCloudDataset(long pointCloudTimestamp, Point[] points, float minDistance, float maxDistance, String origin, int numPoints) {
        this.pointCloudTimestamp = pointCloudTimestamp;
        this.points = points;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.origin = origin;
        this.numPoints = numPoints;
    }

    public long getPointCloudTimestamp() {
        return pointCloudTimestamp;
    }

    public Point[] getPoints() {
        return points;
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
}
