package it.unibo.cvlab.arpydnet;

import android.opengl.Matrix;

import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;

public class CircularAnchor {
    public static final float MIN_SPEED = 0.00f;
    public static final float MAX_SPEED = 0.40f;
    public static final float DEFAULT_SPEED = 0.05f;
    public static final float STEP_SPEED = 0.02f;

    public static final float MIN_RADIUS = 0.00f;
    public static final float MAX_RADIUS = 0.50f;
    public static final float DEFAULT_RADIUS = 0.10f;
    public static final float STEP_RADIUS = 0.02f;


    private final Anchor anchor;
    private final float[] color;

    private float radius = DEFAULT_RADIUS;
    private float angularSpeed = DEFAULT_SPEED;
    private float currentAngle = 0.0f;

    private float x,y;
    private float[] normal;
    private float[] quaternion;

    public CircularAnchor(Anchor a, float[] color4f, Trackable trackable) {
        this.anchor = a;
        this.color = color4f;
        init(trackable);
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public float[] getColor() {
        return color;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public void setAngularSpeed(float angularSpeed) {
        this.angularSpeed = angularSpeed;
    }

    public void setCurrentAngle(float currentAngle) {
        this.currentAngle = currentAngle;
    }

    public void toMatrix(float[] mtx, int offset){
        final Pose pose = anchor.getPose();
        //La matrice viene generata in column-major come richiesto da openGL.
        pose.toMatrix(mtx, 0);

        Matrix.translateM(mtx, 0, x, 0, y);
        Matrix.rotateM(mtx, 0, quaternion[3], quaternion[0], quaternion[1], quaternion[2]);
    }

    public void init(Trackable trackable){
        if(trackable instanceof Plane) {
            final Plane plane = (Plane) trackable;
            final Pose centerPose = plane.getCenterPose();

            normal = new float[3];
            centerPose.getTransformedAxis(1, 1.0f, normal, 0);
            quaternion = centerPose.getRotationQuaternion();
        }else if(trackable instanceof Point){
            final Point point = (Point) trackable;
            final Pose pose = point.getPose();

            normal = new float[3];
            pose.getTransformedAxis(1, 1.0f, normal, 0);
            quaternion = pose.getRotationQuaternion();
        }
    }

    public void update(){
        x = (float) (radius * Math.cos(currentAngle));
        y = (float) (radius * Math.sin(currentAngle));

        currentAngle += angularSpeed;
    }

}
