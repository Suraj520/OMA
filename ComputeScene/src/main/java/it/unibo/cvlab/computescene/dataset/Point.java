package it.unibo.cvlab.computescene.dataset;

import com.google.gson.annotations.Expose;

public class Point implements ITraslation {

    @Expose
    private int x;
    @Expose
    private int y;

    @Expose
    private float tx;
    @Expose
    private float ty;
    @Expose
    private float tz;
    @Expose
    private float confidence;

    @Expose
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

    @Override
    public float getTx() {
        return tx;
    }

    @Override
    public float getTy() {
        return ty;
    }

    @Override
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
