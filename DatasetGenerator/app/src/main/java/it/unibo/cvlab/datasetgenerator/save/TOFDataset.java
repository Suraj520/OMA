package it.unibo.cvlab.datasetgenerator.save;

import com.google.gson.annotations.SerializedName;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import it.unibo.cvlab.datasetgenerator.TOFImageReader;

public class TOFDataset {

    public static TOFDataset parseDataset(TOFImageReader reader){
        ShortBuffer shortDepthBuffer = reader.depth16_raw.asShortBuffer();
        //Forse si può sostiture direttamente con l'array ma non posso verificare. Non ho il telefono adatto.
        List<Point> points = new ArrayList<>(reader.WIDTH * reader.HEIGHT);

        //Suppongo un origine TOP-LEFT
        int x = 0, y = 0;
        short minDistance = Short.MAX_VALUE;
        short maxDistance = Short.MIN_VALUE;

        while(shortDepthBuffer.hasRemaining()){
            //https://developer.android.com/reference/android/graphics/ImageFormat#DEPTH16
            short depthSample = shortDepthBuffer.get();
            short depthRange = (short) (depthSample & 0x1FFF);
            short depthConfidence = (short) ((depthSample >> 13) & 0x7);
            float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;

            if(depthRange < minDistance) minDistance = depthRange;
            if(depthRange > maxDistance) maxDistance = depthRange;

            Point point = new Point(x,y,0, 0, 0, depthPercentage, depthRange / 1000.0f);
            points.add(point);

            if(x < reader.WIDTH){
                x++;
            }
            else {
                x = 0;
                y++;
            }
        }

        Point[] pointsArray = points.toArray(new Point[0]);

        return new TOFDataset("top-left", reader.timestamp, minDistance / 1000.0f, maxDistance / 1000.0f, reader.WIDTH, reader.HEIGHT, pointsArray, pointsArray.length);
    }

    @SerializedName("origin")
    private String origin;

    @SerializedName("tofTimestamp")
    private long tofTimestamp;

    @SerializedName("minDistance")
    private float minDistance;
    @SerializedName("maxDistance")
    private float maxDistance;

    @SerializedName("width")
    private int width;
    @SerializedName("height")
    private int height;

    @SerializedName("points")
    private Point[] points;

    @SerializedName("numPoints")
    private int numPoints;

    @SerializedName("displayRotation")
    private int displayRotation;

    private TOFDataset(String origin, long tofTimestamp, float minDistance, float maxDistance, int width, int height, Point[] points, int numPoints) {
        this.origin = origin;
        this.tofTimestamp = tofTimestamp;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.width = width;
        this.height = height;
        this.points = points;
        this.numPoints = numPoints;
    }

    public String getOrigin() {
        return origin;
    }

    public long getTofTimestamp() {
        return tofTimestamp;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Point[] getPoints() {
        return points;
    }

    public int getNumPoints() {
        return numPoints;
    }

    public int getDisplayRotation() {
        return displayRotation;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }
}
