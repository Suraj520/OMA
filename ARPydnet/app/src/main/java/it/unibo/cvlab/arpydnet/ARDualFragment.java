package it.unibo.cvlab.arpydnet;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.ar.core.examples.java.common.helpers.TapHelper;

import butterknife.Unbinder;
import it.unibo.cvlab.pydnet.ImageUtils;
import it.unibo.cvlab.pydnet.Utils;
import it.unibo.cvlab.pydnet.demo.OverlayView;

public class ARDualFragment extends Fragment {

    private static final String TAG = ARDualFragment.class.getSimpleName();

    //Usato per il rendering opengl. Implementato nell'activity
    private GLSurfaceView.Renderer renderer;
    //Usato per gestire il tocco dell'utente
    private TapHelper tapHelper;

    private GLSurfaceView mySurfaceView;
    private OverlayView depthView;

    private Unbinder unbinder;

    private Bitmap depthImage;
    private Matrix transformationMatrix = new Matrix();

    private int previewWidth, previewHeight;
    private Utils.Resolution resolution;

    public ARDualFragment(GLSurfaceView.Renderer renderer, TapHelper tapHelper) {
        super();
        this.renderer = renderer;
        this.tapHelper = tapHelper;
    }

    public ARDualFragment(int contentLayoutId, GLSurfaceView.Renderer renderer, TapHelper tapHelper) {
        super(contentLayoutId);
        this.renderer = renderer;
        this.tapHelper = tapHelper;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_fragment_double, container, false);

        if(view != null){
            mySurfaceView = view.findViewById(R.id.doubleSurfaceView);
            depthView = view.findViewById(R.id.doubleDepthOverlay);

            mySurfaceView.setOnTouchListener(tapHelper);

            // Set up renderer.
            mySurfaceView.setPreserveEGLContextOnPause(true);
            //Provo un contesto 3.0, che mi serve per il caricamento della maschera di float.
            mySurfaceView.setEGLContextClientVersion(3);
            mySurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 8); // Alpha used for plane blending.
            mySurfaceView.setRenderer(renderer);
            mySurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            mySurfaceView.setWillNotDraw(false);

            depthView.addCallback(canvas -> {
                if(depthImage != null){
                    int cx = (previewWidth - depthImage.getWidth()) / 2;
                    int cy = (previewHeight - depthImage.getHeight()) / 2;

                    canvas.drawBitmap(depthImage, cx, cy, null);
                }
            });
        }

        return view;
    }

    public void updateDepthImage(Bitmap depthImage){

        int tmpWidth, tmpHeight;

        if(resolution.getWidth() > resolution.getHeight()){
            float scaleFactor = (float) resolution.getWidth() / resolution.getHeight();
            tmpWidth = Math.round(scaleFactor * previewHeight);
            tmpHeight = previewHeight;
        }else{
            float scaleFactor = (float) resolution.getHeight() / resolution.getWidth();
            tmpWidth = previewWidth;
            tmpHeight = Math.round(scaleFactor * previewWidth);
        }

        this.depthImage = Bitmap.createScaledBitmap(depthImage, tmpWidth, tmpHeight, false);
    }


    public void updateData(int previewWidth, int previewHeight, Utils.Resolution resolution){
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.resolution = resolution;
    }

    public void requestRender() {
        if (depthView != null) {
            depthView.postInvalidate();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mySurfaceView != null)
            mySurfaceView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        if(mySurfaceView != null)
            mySurfaceView.onResume();
    }
}
