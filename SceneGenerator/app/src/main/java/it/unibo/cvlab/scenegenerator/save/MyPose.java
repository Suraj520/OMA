package it.unibo.cvlab.scenegenerator.save;

import com.google.ar.core.Pose;

public class MyPose {

    private float tx,ty,tz;
    private float qx;
    private float qy;
    private float qz;
    private float qw;
    private float[] modelMatrix;
    private float[] modelViewMatrix;
    private float[] modelViewProjectionMatrix;

    public MyPose(Pose pose, float[] modelMatrix, float[] modelViewMatrix, float[] modelViewProjectionMatrix){
        this.tx = pose.tx();
        this.ty = pose.ty();
        this.tz = pose.tz();
        this.qx = pose.qx();
        this.qy = pose.qy();
        this.qz = pose.qz();
        this.qw = pose.qw();
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
