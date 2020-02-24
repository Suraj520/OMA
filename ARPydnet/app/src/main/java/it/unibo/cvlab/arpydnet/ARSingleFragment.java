package it.unibo.cvlab.arpydnet;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.ar.core.examples.java.common.helpers.TapHelper;

import butterknife.Unbinder;
import it.unibo.cvlab.pydnet.demo.Size;

public class ARSingleFragment extends Fragment {

    private static final String TAG = ARSingleFragment.class.getSimpleName();

    //Usato per il rendering opengl. Implementato nell'activity
    private GLSurfaceView.Renderer renderer;
    //Usato per gestire il tocco dell'utente
    private TapHelper tapHelper;

    private GLSurfaceView mySurfaceView;

    private Unbinder unbinder;


    public ARSingleFragment(GLSurfaceView.Renderer renderer, TapHelper tapHelper) {
        super();
        this.renderer = renderer;
        this.tapHelper = tapHelper;
    }

    public ARSingleFragment(int contentLayoutId, GLSurfaceView.Renderer renderer, TapHelper tapHelper) {
        super(contentLayoutId);
        this.renderer = renderer;
        this.tapHelper = tapHelper;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_fragment_single, container, false);

        if(view != null){
            mySurfaceView = view.findViewById(R.id.singleSurfaceView);

            mySurfaceView.setOnTouchListener(tapHelper);

            // Set up renderer.
            mySurfaceView.setPreserveEGLContextOnPause(true);
            //Provo un contesto 3.0, che mi serve per il caricamento della maschera di float.
            mySurfaceView.setEGLContextClientVersion(3);
            mySurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 8); // Alpha used for plane blending.
            mySurfaceView.setRenderer(renderer);
            mySurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            mySurfaceView.setWillNotDraw(false);
        }
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        mySurfaceView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mySurfaceView.onResume();
    }
}
