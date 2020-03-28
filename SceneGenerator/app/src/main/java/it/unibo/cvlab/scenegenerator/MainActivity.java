package it.unibo.cvlab.scenegenerator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.gson.Gson;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import com.google.ar.core.examples.java.common.helpers.*;
import com.google.ar.core.examples.java.common.rendering.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Gson GSON = new Gson();

    private static final int datasetPath = R.string.dataset_path;
    private static final int imagesPath = R.string.images_path;
    private static final int posesPath = R.string.poses_path;

    private static final float NEAR_PLANE  = 0.1f;
    private static final float FAR_PLANE  = 10.0f;

    @BindView(R.id.surfaceView)
    GLSurfaceView mySurfaceView;

    @BindView(R.id.optionsToolbar)
    Toolbar optionsToolbar;

    private Snackbar loadingMessageSnackbar = null;

    private boolean installRequested;
    private Session session;

    //Helper vari
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;//Usato per gestire il tocco dell'utente

    //Usati per il rendering degli oggetti virtuali:
    //Usano direttamente opengl
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

    private boolean recordOn = false;

    private int surfaceWidth, surfaceHeight;

    private String datasetPathString;
    private String imagesPathString;
    private String posesPathString;

    private String datasetIdentifier;

    private int fileNameCounter;

    private boolean savingFrame = false;

    //Usato per far girare il salvataggio dell'immagine in un altro thread.
    private Handler handler;
    private HandlerThread handlerThread;

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastTimestamp = 0;

    private int[] pixelData;
    private IntBuffer pixelDataBuffer;
    private int[] bitmapData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Binding tra XML e Java tramite ButterKnife
        ButterKnife.bind(this);

        //Helpers
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        // Set up tap listener.
        tapHelper = new TapHelper(this);

        Menu optionsMenu = optionsToolbar.getMenu();
        optionsMenu.findItem(R.id.toggle_record).setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);

        optionsToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.toggle_record) {
                if(!isExternalStorageAvailable() || isExternalStorageReadOnly()){
                    recordOn = false;
                }
                else{
                    if(recordOn){
                        recordOn = false;
                    }else{
                        //Creazione di un nuovo identificatore basato sulla data.
                        SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault());
                        datasetIdentifier = sdf.format(new Date());
                        //Resetto il contatore.
                        fileNameCounter = 0;
                        recordOn = true;
                    }
                }

                item.setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);

                return true;
            }

            return false;
        });

        installRequested = false;
    }

    public void onSessionResume(){
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

                Config config = new Config(session);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                session.configure(config);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                showLoadingMessage(message, Snackbar.LENGTH_LONG);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            showLoadingMessage(R.string.camera_not_available, Snackbar.LENGTH_LONG);
            session = null;
            return;
        }

        displayRotationHelper.onResume();
    }

    @Override
    protected void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("handler");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        onSessionResume();
    }

    public void onSessionPause(){
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            session.pause();
        }
    }

    @Override
    public void onPause() {
        handlerThread.quitSafely();

        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Impossibile fermare handler pydnet", e);
        }

        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(this);

            ShaderUtil.checkGLError(TAG, "OnSurfaceCreated");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        pixelData = new int[surfaceWidth * surfaceHeight];
        pixelDataBuffer = IntBuffer.wrap(pixelData);
        bitmapData = new int[surfaceWidth * surfaceHeight];
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }


    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }

        Frame frame;

        try {
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            displayRotationHelper.updateSessionIfNeeded(session);

            int displayRotation = displayRotationHelper.getDisplayRotation();

            //IMPORTANTE: fa il binding tra la texutre che andrà in background e la camera.
            session.setCameraTextureName(backgroundRenderer.getBackgroundTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            frame = session.update();

            Camera camera = frame.getCamera();
            Pose cameraPose = camera.getDisplayOrientedPose();

            // Get projection matrix.
            //La matrice viene generata in column-major come richiesto da openGL.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, NEAR_PLANE, FAR_PLANE);

            // Get camera matrix and draw.
            //La matrice viene generata in column-major come richiesto da openGL.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                String trackingFailureReasonString = TrackingStateHelper.getTrackingFailureReasonString(camera);
                if(!trackingFailureReasonString.isEmpty())
                    runOnUiThread(()->showLoadingMessage(trackingFailureReasonString, Snackbar.LENGTH_LONG));
                return;
            }

            //Qui inizia il disegno 3D.

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // No tracking error at this point. If we detected any plane, then hide the
            // message UI, otherwise show searchingPlane message.
            if (!hasTrackingPlane()) {
                runOnUiThread(()->showLoadingMessage(R.string.searching_plane_message, Snackbar.LENGTH_INDEFINITE));
            } else {
                runOnUiThread(this::hideLoadingMessage);
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }

    //https://github.com/FilippoAleotti/mobilePydnet/
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    //https://www.journaldev.com/9400/android-external-storage-read-write-save-file

    private static boolean isExternalStorageReadOnly() {
        String extStorageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState);
    }

    private static boolean isExternalStorageAvailable() {
        String extStorageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(extStorageState);
    }

    private void showLoadingMessage(int resID, int length) {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        findViewById(R.id.container),
                        resID,
                        length);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void showLoadingMessage(String text, int length) {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        findViewById(R.id.container),
                        text,
                        length);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }
}
