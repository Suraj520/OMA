package it.unibo.cvlab.datasetgenerator.save;

import com.google.ar.core.Pose;

public class MyPose {

    private float tx,ty,tz;
    private float qx,qy,qz,qw;

    public MyPose(Pose pose){
        this(pose.tx(), pose.ty(), pose.tz(), pose.qx(), pose.qy(), pose.qz(), pose.qw());
    }

    public MyPose(float tx, float ty, float tz, float qx, float qy, float qz, float qw) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
        this.qw = qw;
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
}
