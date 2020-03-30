package it.unibo.cvlab.scenegenerator;

import android.opengl.Matrix;

import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;

public class CustomAnchor {

    public static final float DEFAULT_SPEED = 0.05f;
    public static final float DEFAULT_HEIGHT_FACTOR = 0.01f;

    private final Anchor anchor;

    private float currentAngle = 0.0f;
    private float angularSpeed = DEFAULT_SPEED;
    private float heightFactor = DEFAULT_HEIGHT_FACTOR;

    private float z;
    private float[] quaternion;

    public CustomAnchor(Anchor a, Trackable trackable) {
        this.anchor = a;
        init(trackable);
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAngularSpeed(float angularSpeed) {
        this.angularSpeed = angularSpeed;
    }

    public void setHeightFactor(float heightFactor) {
        this.heightFactor = heightFactor;
    }

    public void toMatrix(float[] mtx, int offset){
        final Pose pose = anchor.getPose();
        //La matrice viene generata in column-major come richiesto da openGL.
        pose.toMatrix(mtx, 0);

        Matrix.translateM(mtx, 0, 0, z, 0);
        Matrix.rotateM(mtx, 0, quaternion[3], quaternion[0], quaternion[1], quaternion[2]);
    }

    private void init(Trackable trackable){
        if(trackable instanceof Plane) {
            final Plane plane = (Plane) trackable;
            final Pose centerPose = plane.getCenterPose();
            quaternion = centerPose.getRotationQuaternion();
        }else if(trackable instanceof Point){
            final Point point = (Point) trackable;
            final Pose pose = point.getPose();
            quaternion = pose.getRotationQuaternion();
        }
    }

    public void update(){
        z = (float) (heightFactor * Math.sin(currentAngle));
        currentAngle += angularSpeed;
    }

}
