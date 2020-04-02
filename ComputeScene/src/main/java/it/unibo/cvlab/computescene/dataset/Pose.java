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


    public Pose(float tx, float ty, float tz, float qx, float qy, float qz, float qw) {
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

    public float[] toMatrix(){
        float[] array = new float[16];
        toMatrix(array);
        return array;
    }

    public Matrix4f toMatrix4f(){
        return new Matrix4f(toMatrix());
    }

    //Metodo decompilati da Arcore Pose

    public void toMatrix(float[] array) {
        float normaQuadrata = this.qx * this.qx + this.qy * this.qy + this.qz * this.qz + this.qw * this.qw;
        float normalizzazione = normaQuadrata > 0.0F ? 2.0F / normaQuadrata : 0.0F;

        float pWX = this.qw * this.qx * normalizzazione;
        float pWY = this.qw * this.qy * normalizzazione;
        float pWZ = this.qw * this.qz * normalizzazione;
        float pXX = this.qx * this.qx * normalizzazione;
        float pXY = this.qx * this.qy * normalizzazione;
        float pXZ = this.qx * this.qz * normalizzazione;
        float pYY = this.qy * this.qy * normalizzazione;
        float pYZ = this.qy * this.qz * normalizzazione;
        float pZZ = this.qz * this.qz * normalizzazione;

        array[0] = 1.0F - (pYY + pZZ);
        array[1] = pXY + pWZ;
        array[2] = pXZ - pWY;
        array[3] = 0.0F;
        array[4] = pXY - pWZ;
        array[5] = 1.0F - (pXX + pZZ);
        array[6] = pYZ + pWX;
        array[7] = 0.0F;
        array[8] = pXZ + pWY;
        array[9] = pYZ - pWX;
        array[10] = 1.0F - (pXX + pYY);
        array[11] = 0.0F;
        array[12] = this.tx;
        array[13] = this.ty;
        array[14] = this.tz;
        array[15] = 1.0F;
    }

}
