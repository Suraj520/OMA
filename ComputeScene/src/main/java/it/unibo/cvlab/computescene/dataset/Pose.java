package it.unibo.cvlab.computescene.dataset;

import com.google.gson.annotations.Expose;
import silvertiger.tutorial.lwjgl.math.Matrix4f;

public class Pose {

    @Expose
    private float tx;
    @Expose
    private float ty;
    @Expose
    private float tz;

    @Expose
    private float qx;
    @Expose
    private float qy;
    @Expose
    private float qz;
    @Expose
    private float qw;

    @Expose
    private float[] modelMatrix;
    @Expose
    private float[] modelViewMatrix;
    @Expose
    private float[] modelViewProjectionMatrix;

    public Pose(float tx, float ty, float tz, float qx, float qy, float qz, float qw, float[] modelMatrix, float[] modelViewMatrix, float[] modelViewProjectionMatrix) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
        this.qw = qw;
        this.modelMatrix = modelMatrix;
        this.modelViewMatrix = modelViewMatrix;
        this.modelViewProjectionMatrix = modelViewProjectionMatrix;
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

    public float getQx() {
        return qx;
    }

    public float getQy() {
        return qy;
    }

    public float getQz() {
        return qz;
    }

    public float getQw() {
        return qw;
    }

    public float[] getModelMatrix() {
        return modelMatrix;
    }

    public float[] getModelViewMatrix() {
        return modelViewMatrix;
    }

    public float[] getModelViewProjectionMatrix() {
        return modelViewProjectionMatrix;
    }
}
