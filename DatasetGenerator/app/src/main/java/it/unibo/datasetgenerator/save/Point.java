package it.unibo.datasetgenerator.save;

public class Point {

    private int x,y;
    private float tx,ty,tz;
    private float confidence;

    private float distance;

    public Point(int x, int y, float tx, float ty, float tz, float confidence, float distance) {
        this.x = x;
        this.y = y;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.confidence = confidence;
        this.distance = distance;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public float getTx() {
        return tx;
    }

    public float getTy() {
        return ty;
    }

    public float getTz() {
        return tz;
    }

    public float getConfidence() {
        return confidence;
    }

    public float getDistance() {
        return distance;
    }
}
