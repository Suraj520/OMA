package it.unibo.cvlab.arpydnet;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.examples.java.common.rendering.ShaderUtil;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.ButterKnife;
import it.unibo.cvlab.pydnet.ColorMapper;
import it.unibo.cvlab.pydnet.Model;
import it.unibo.cvlab.pydnet.ModelFactory;
import it.unibo.cvlab.pydnet.Utils;

//Inspirata da:
//https://github.com/FilippoAleotti/mobilePydnet/
//https://github.com/google-ar/arcore-android-sdk/tree/master/samples/hello_ar_java


public class ARPydnet extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = ARPydnet.class.getSimpleName();

    public static final float NEAR_PLANE = 0.2f;
    public static final float FAR_PLANE = 5.0f;

    @BindView(R.id.container)
    FragmentContainerView fragmentContainerView;

    @BindView(R.id.optionsToolbar)
    Toolbar optionsToolbar;

    @BindView(R.id.logTextView)
    TextView logTextView;

    private Snackbar loadingMessageSnackbar = null;

    private boolean installRequested;
    private Session session;

    //Fragments
    private Fragment currentFragment;
    private ARSingleFragment singleFragment;
    private ARDualFragment dualFragment;

    //Helper vari
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;//Usato per gestire il tocco dell'utente

    //Usati per il rendering degli oggetti virtuali:
    //Usano direttamente opengl
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    private final PointerRenderer pointerRenderer = new PointerRenderer();
    private final ScreenshotRenderer screenshotRenderer = new ScreenshotRenderer();

    //Ricavo il numero di thread massimi.
    private static int NUMBER_THREADS = Runtime.getRuntime().availableProcessors();
    private static final float OBJ_SCALE_FACTOR = 1.0f;

    private Model currentModel;
    private FloatBuffer inference;

    //Usato per far girare pydnet in un altro thread.
    private Handler inferenceHandler;
    private HandlerThread inferenceHandlerThread;

    //Usato per indicare che la rete neurale sta effettuando una conversione.
    private volatile boolean isProcessingFrame = false;

    //Qui parametri fissi della pydnet: dipendono dal modello caricato.
    private static final Utils.Resolution RESOLUTION = Utils.Resolution.RES4;
    private static final Utils.Scale SCALE = Utils.Scale.HALF;
    private static final float MAPPER_SCALE_FACTOR = 0.2f;
    private static final float COLOR_SCALE_FACTOR = 10.5f;
    private static final int QUANTIZER_LEVELS = 64;

    //Oggetti usati per l'unione della Pydnet e ARCore
    //Immagine dalla camera: usata per la maschera e per la pydnet.
    Image cameraImage = null;

    //Usato per calibrare le maschere degli oggetti.
    private Calibrator calibrator = null;
    //Usato per generare il depth color.
    private ColorMapper colorMapper = null;

    private boolean depthColorEnabled = false;
    private boolean maskEnabled = false;
    private boolean quantizedMaskEnabled = false;
    private boolean pointerMode = false;
    private boolean dualScreenMode = false;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];

    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {
        private final Anchor anchor;
        private final float[] color;

        private ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arpydnet);

        //Binding tra XML e Java tramite ButterKnife
        ButterKnife.bind(this);

        //Posso attivare il depth color solo se sto in single mode.
        depthColorEnabled = !dualScreenMode && depthColorEnabled;

        //Helpers
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        // Set up tap listener.
        tapHelper = new TapHelper(this);

        //Fragments
        singleFragment = new ARSingleFragment(this, tapHelper);
        dualFragment = new ARDualFragment(this, tapHelper);
        currentFragment = dualScreenMode ? dualFragment : singleFragment;

        FragmentTransaction fragTransaction = getSupportFragmentManager().beginTransaction();
        fragTransaction.add(R.id.container, currentFragment);
        fragTransaction.commit();

        //Layout etc

        Menu optionsMenu = optionsToolbar.getMenu();
        optionsMenu.findItem(R.id.toggle_depth_color).setIcon(depthColorEnabled ? R.drawable.ic_depth_color : R.drawable.ic_depth_color_off);
        optionsMenu.findItem(R.id.toggle_mask).setIcon(maskEnabled ? R.drawable.ic_mask : R.drawable.ic_mask_off);
        optionsMenu.findItem(R.id.toggle_quantize).setIcon(quantizedMaskEnabled ? R.drawable.ic_quantize : R.drawable.ic_quantize_off);
        optionsMenu.findItem(R.id.toggle_pointer).setIcon(pointerMode ? R.drawable.ic_pointer : R.drawable.ic_pointer_off);
        optionsMenu.findItem(R.id.toggle_dual_screen).setIcon(dualScreenMode ? R.drawable.ic_dual_screen : R.drawable.ic_dual_screen_off);

        //Attivo il depth solo se non siamo in dual mode.
//        optionsMenu.findItem(R.id.toggle_depth_color).setEnabled(!dualScreenMode);

        optionsToolbar.setOnMenuItemClickListener(item -> {

            switch (item.getItemId()) {
                case R.id.toggle_depth_color:
                    depthColorEnabled = !depthColorEnabled;
                    item.setIcon(depthColorEnabled ? R.drawable.ic_depth_color : R.drawable.ic_depth_color_off);
                    return true;

                case R.id.toggle_mask:
                    maskEnabled = !maskEnabled;
                    item.setIcon(maskEnabled ? R.drawable.ic_mask : R.drawable.ic_mask_off);
                    return true;

                case R.id.toggle_quantize:
                    quantizedMaskEnabled = !quantizedMaskEnabled;
                    item.setIcon(quantizedMaskEnabled ? R.drawable.ic_quantize : R.drawable.ic_quantize_off);
                    return true;

                case R.id.toggle_pointer:
                    pointerMode = !pointerMode;
                    item.setIcon(pointerMode ? R.drawable.ic_pointer : R.drawable.ic_pointer_off);
                    return true;

                case R.id.toggle_dual_screen:{
                    dualScreenMode = !dualScreenMode;
                    item.setIcon(dualScreenMode ? R.drawable.ic_dual_screen : R.drawable.ic_dual_screen_off);

                    //Disabilito altre opzioni.
                    maskEnabled = false;
                    depthColorEnabled = false;
                    optionsMenu.findItem(R.id.toggle_depth_color).setIcon(depthColorEnabled ? R.drawable.ic_depth_color : R.drawable.ic_depth_color_off);
                    optionsMenu.findItem(R.id.toggle_mask).setIcon(maskEnabled ? R.drawable.ic_mask : R.drawable.ic_mask_off);

                    //Attivo il depth solo se non siamo in dual mode.
//                    optionsMenu.findItem(R.id.toggle_depth_color).setEnabled(!dualScreenMode);

                    onSessionPause();
                    session.close();
                    session = null;

                    //Cambio il fragment.

                    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();

                    if (dualScreenMode) {
                        fragmentTransaction.replace(R.id.container, dualFragment);
                        fragmentTransaction.commit();

                        currentFragment = dualFragment;
                    } else {
                        fragmentTransaction.replace(R.id.container, singleFragment);
                        fragmentTransaction.commit();

                        currentFragment = singleFragment;
                    }

                    onSessionResume();

                    return true;
                }
                case R.id.reset: {
                    anchors.clear();

                    //Disabilito altre opzioni.
                    maskEnabled = false;
                    depthColorEnabled = false;
                    optionsMenu.findItem(R.id.toggle_depth_color).setIcon(depthColorEnabled ? R.drawable.ic_depth_color : R.drawable.ic_depth_color_off);
                    optionsMenu.findItem(R.id.toggle_mask).setIcon(maskEnabled ? R.drawable.ic_mask : R.drawable.ic_mask_off);

                    if (session != null) {
                        onSessionPause();
                        session.close();
                        session = null;
                        onSessionResume();
                    }

                    return true;
                }
                default:
                    return false;
            }
        });

        //Pydnet: ricavo il modello.
        //Oggetti per la pydnet
        //TODO: Da spostare nel GL context 3.1 quando capisci come fare...
        ModelFactory modelFactory = new ModelFactory(getApplicationContext());
        currentModel = modelFactory.getModel(0);
        currentModel.prepare(RESOLUTION);

        calibrator = new Calibrator(MAPPER_SCALE_FACTOR, RESOLUTION, NUMBER_THREADS);

        colorMapper = new ColorMapper(COLOR_SCALE_FACTOR, true, NUMBER_THREADS);
        colorMapper.prepare(RESOLUTION);

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

                Config.LightEstimationMode lightEstimationMode =
                        Config.LightEstimationMode.AMBIENT_INTENSITY;

                Config config = new Config(session);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                config.setLightEstimationMode(lightEstimationMode);
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

        currentFragment.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    protected void onResume() {
        super.onResume();

        inferenceHandlerThread = new HandlerThread("inference");
        inferenceHandlerThread.start();
        inferenceHandler = new Handler(inferenceHandlerThread.getLooper());

        onSessionResume();
    }

    public void onSessionPause(){
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            currentFragment.onPause();
            session.pause();
        }
    }

    @Override
    public void onPause() {
        inferenceHandlerThread.quitSafely();

        try {
            inferenceHandlerThread.join();
            inferenceHandlerThread = null;
            inferenceHandler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Impossibile fermare handler pydnet", e);
        }

        super.onPause();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (calibrator != null)
                calibrator.dispose();

            if (colorMapper != null)
                colorMapper.dispose();
        } catch (InterruptedException e) {
            Log.d(TAG, "Thread Interrotto nel join", e);
        }
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

    //Metodi per il draw opengl

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);

            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow.createOnGlThread(
                    /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

            pointerRenderer.createOnGlThread(this, "models/mirino.png");

            screenshotRenderer.createOnGlThread(this);
            screenshotRenderer.updateScaledFrameBuffer(RESOLUTION);

            ShaderUtil.checkGLError(TAG, "OnSurfaceCreated");

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        backgroundRenderer.onSurfaceChanged(width, height);
        screenshotRenderer.onSurfaceChanged(width, height);
        calibrator.onSurfaceChanged(width, height);
        pointerRenderer.onSurfaceChanged(width, height);

        displayRotationHelper.onSurfaceChanged(width, height);

        dualFragment.onSurfaceChanged(width, height, RESOLUTION);

        //Lo faccio qui dove il background renderer ha già istanziato la texture necessaria all'altro programma.
        screenshotRenderer.setColorFrameBufferTextureId(backgroundRenderer.getScreenshotFrameBufferTextureId());

        GLES20.glViewport(0, 0, width, height);
    }

    //https://github.com/FilippoAleotti/mobilePydnet/
    protected synchronized void runInBackground(final Runnable r) {
        if (inferenceHandler != null) {
            inferenceHandler.post(r);
        }
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

            calibrator.setDisplayRotation(displayRotation);

            planeRenderer.setScreenOrientation(displayRotation);
            virtualObject.setScreenOrientation(displayRotation);
            virtualObjectShadow.setScreenOrientation(displayRotation);

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

            calibrator.setCameraView(viewmtx);
            calibrator.setCameraPerspective(projmtx);

            // Handle one tap per frame.
            handleTap(frame, camera);

            planeRenderer.setScaleFactor(calibrator.getScaleFactor());
            planeRenderer.setCameraPose(cameraPose);

            virtualObject.setScaleFactor(calibrator.getScaleFactor());
            virtualObject.setCameraPose(cameraPose);

            virtualObjectShadow.setScaleFactor(calibrator.getScaleFactor());
            virtualObjectShadow.setCameraPose(cameraPose);

            //Impostazione delle maschere
            if (inference != null) {
                //Maschere
                planeRenderer.setMaskEnabled(maskEnabled);
                virtualObject.setMaskEnabled(maskEnabled);
                virtualObjectShadow.setMaskEnabled(maskEnabled);

                //Impostazione del colore di profondità
                backgroundRenderer.setDepthColorEnabled(depthColorEnabled);
                planeRenderer.setDepthColorEnabled(depthColorEnabled);
                virtualObject.setDepthColorEnabled(depthColorEnabled);
                virtualObjectShadow.setDepthColorEnabled(depthColorEnabled);

                planeRenderer.setQuantizedMaskEnabled(quantizedMaskEnabled);
                virtualObject.setQuantizedMaskEnabled(quantizedMaskEnabled);
                virtualObjectShadow.setQuantizedMaskEnabled(quantizedMaskEnabled);

                if(maskEnabled){
                    if(quantizedMaskEnabled){
                        Bitmap quantizedDepthMap = calibrator.getQuantizedDepthMap(inference, NUMBER_THREADS, QUANTIZER_LEVELS);
                        planeRenderer.setQuantizerFactor(calibrator.getQuantizerFactor());
                        virtualObject.setQuantizerFactor(calibrator.getQuantizerFactor());
                        virtualObjectShadow.setQuantizerFactor(calibrator.getQuantizerFactor());

                        planeRenderer.loadMaskImage(quantizedDepthMap);
                        virtualObject.loadMaskImage(quantizedDepthMap);
                        virtualObjectShadow.loadMaskImage(quantizedDepthMap);

                        quantizedDepthMap.recycle();
                    }else{
                        planeRenderer.loadMask(inference, RESOLUTION);
                        virtualObject.loadMask(inference, RESOLUTION);
                        virtualObjectShadow.loadMask(inference, RESOLUTION);
                    }
                }

                if (depthColorEnabled || dualScreenMode) {
                    Bitmap bitmapDepthColor = colorMapper.getColorMap(inference, NUMBER_THREADS);


                    if(depthColorEnabled){
                        backgroundRenderer.loadDepthColorImage(bitmapDepthColor);

                        planeRenderer.loadDepthColorImage(bitmapDepthColor);
                        virtualObject.loadDepthColorImage(bitmapDepthColor);
                        virtualObjectShadow.loadDepthColorImage(bitmapDepthColor);
                    }

                    if(dualScreenMode){
                        dualFragment.updateDepthImage(bitmapDepthColor);
                        dualFragment.requestRender();
                    }

                    bitmapDepthColor.recycle();
                }
            } else {
                planeRenderer.setMaskEnabled(false);
                virtualObject.setMaskEnabled(false);
                virtualObjectShadow.setMaskEnabled(false);

                backgroundRenderer.setDepthColorEnabled(false);
                planeRenderer.setDepthColorEnabled(false);
                virtualObject.setDepthColorEnabled(false);
                virtualObjectShadow.setDepthColorEnabled(false);
            }

            //Qui inizia il disegno dello sfondo (camera)
            // If frame is ready, render camera preview image to the GL surface
            backgroundRenderer.draw(frame);

            //Devo ricavarlo dopo il rendering dello sfondo.
            //Ricavo il frame da opengl: il frame della camera è diverso da quello di arcore.
            if(!isProcessingFrame){
                //Profile: 100 ms
                //Vecchio metodo: 160 ms

                long nanos = SystemClock.elapsedRealtime();
                //20 ms
                int[] screenshot = screenshotRenderer.screenshot();

                Log.d(TAG, "screenshot take: " + (SystemClock.elapsedRealtime()-nanos));
                nanos = SystemClock.elapsedRealtime();

                //80 ms
                currentModel.loadInput(screenshot);
                Log.d(TAG, "LoadInput take: " + (SystemClock.elapsedRealtime()-nanos));

                //Posso far partire il modello.
                isProcessingFrame = true;

                runInBackground(() -> {
                    inference = currentModel.doInference(SCALE);
                    isProcessingFrame = false;
                });
            }

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

            // Visualize tracked points.
            // Use try-with-resources to automatically release the point cloud.
            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                if(inference != null)
                    calibrator.calibrateScaleFactor(inference, pointCloud, cameraPose);

                //Il numero visible points è indicativo, ci potrebbero essere più punti rispetto
                //A quelli sullo schermo
                runOnUiThread(()->setGeneralLogMessage(calibrator.getNumVisiblePoints()));

                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewmtx, projmtx);
            }

            // No tracking error at this point. If we detected any plane, then hide the
            // message UI, otherwise show searchingPlane message.
            if (!hasTrackingPlane()) {
                runOnUiThread(()->showLoadingMessage(R.string.searching_plane_message, Snackbar.LENGTH_INDEFINITE));
                runOnUiThread(this::setNoLogMessage);
            } else {
                runOnUiThread(this::hideLoadingMessage);
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            // Visualize anchors created by touch.
            for (ColoredAnchor coloredAnchor : anchors) {

                if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }

                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                Pose objPose = coloredAnchor.anchor.getPose();

                //La matrice viene generata in column-major come richiesto da openGL.
                objPose.toMatrix(anchorMatrix, 0);

                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, OBJ_SCALE_FACTOR);
                virtualObjectShadow.updateModelMatrix(anchorMatrix, OBJ_SCALE_FACTOR);

                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            }

            if(pointerMode){
                pointerRenderer.draw();
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        } finally {
            if (cameraImage != null)
                cameraImage.close();
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();

        if (tap != null) {
            if (pointerMode) {
                int x = Math.round(tap.getRawX());
                int y = Math.round(tap.getRawY());

                pointerRenderer.setXY(x,y);
            } else{
                if (camera.getTrackingState() == TrackingState.TRACKING) {
                    for (HitResult hit : frame.hitTest(tap)) {

                        // Check if any plane was hit, and if it was hit inside the plane polygon
                        Trackable trackable = hit.getTrackable();
                        // Creates an anchor if a plane or an oriented point was hit.

                        if ((trackable instanceof Plane
                                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                                && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                                || (trackable instanceof Point
                                && ((Point) trackable).getOrientationMode()
                                == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {

                            // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                            // Cap the number of objects created. This avoids overloading both the
                            // rendering system and ARCore.
                            if (anchors.size() >= 20) {
                                anchors.get(0).anchor.detach();
                                anchors.remove(0);
                            }

                            // Assign a color to the object for rendering.
                            Anchor anchor = hit.createAnchor();

                            float[] myMatrix = new float[16];
                            anchor.getPose().toMatrix(myMatrix, 0);

//                            Log.d(TAG, "Raw XY: " + tap.getRawX() + ", " + tap.getRawY());
//                            Log.d(TAG, "XY test: " + calibrator.xyTest(anchor.getPose(), tap.getRawX(), tap.getRawY()));
//                            Log.d(TAG, "Calibration test: " + calibrator.calibrationTest(inference, anchor.getPose(), camera.getDisplayOrientedPose(), tap.getRawX(), tap.getRawY()));

                            float[] objColor = new float[]{233.0f, 233.0f, 244.0f, 255.0f};

                            // Adding an Anchor tells ARCore that it should track this position in
                            // space. This anchor is created on the Plane to place the 3D model
                            // in the correct position relative both to the world and to the plane.
                            anchors.add(new ColoredAnchor(anchor, objColor));

                            break;
                        }
                    }
                }
            }
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

    private float getDistance(Pose objPose, Pose cameraPose) {
        float dx = cameraPose.tx() - objPose.tx();
        float dy = cameraPose.ty() - objPose.ty();
        float dz = cameraPose.tz() - objPose.tz();

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    private void setGeneralLogMessage(int numPoints){
        String text = getString(R.string.log_general, calibrator.getScaleFactor(), numPoints, calibrator.getMinDistance(), calibrator.getMaxDistance());
        logTextView.setText(text);
    }

    private void setNoLogMessage(){
        logTextView.setText(R.string.no_log_message);
    }

}
