package it.unibo.cvlab.scenegenerator;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
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
import com.google.ar.core.examples.java.common.rendering.ShaderUtil;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.ButterKnife;
import it.unibo.cvlab.scenegenerator.save.SceneDataset;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Gson GSON = new Gson();

    private static final int datasetPath = R.string.dataset_path;
    private static final int imagesPath = R.string.images_path;
    private static final int scenesPath = R.string.scenes_path;

    private static final float NEAR_PLANE  = 0.1f;
    private static final float FAR_PLANE  = 10.0f;

    private static final Size SCREENSHOT_SIZE = new Size(1024, 574);

    @BindView(R.id.surfaceView)
    GLSurfaceView mySurfaceView;

    @BindView(R.id.optionsToolbar)
    Toolbar optionsToolbar;

    private Menu optionsMenu;

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
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final ScreenshotRenderer screenshotRenderer = new ScreenshotRenderer();
    private final ObjectRenderer objectRenderer = new ObjectRenderer();

    private boolean recordOn = false;

    private int surfaceWidth, surfaceHeight;

    private Bitmap bmp = Bitmap.createBitmap(SCREENSHOT_SIZE.width, SCREENSHOT_SIZE.height, Bitmap.Config.ARGB_8888);

    private String datasetPathString;
    private String imagesPathString;
    private String scenesPathString;

    private String datasetIdentifier;

    private int fileNameCounter;

    //Usato per far girare il salvataggio dell'immagine in un altro thread.
    private Handler handler;
    private HandlerThread handlerThread;

    private long frameCounter = 0L;

    private final float[] anchorMatrix = new float[16];
    private final ArrayList<CustomAnchor> anchors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        datasetPathString = getString(datasetPath);
        imagesPathString = getString(imagesPath);
        scenesPathString = getString(scenesPath);

        //Binding tra XML e Java tramite ButterKnife
        ButterKnife.bind(this);

        //Helpers
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        // Set up tap listener.
        tapHelper = new TapHelper(this);

        optionsMenu = optionsToolbar.getMenu();
        optionsMenu.findItem(R.id.toggle_record).setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);

        optionsToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.toggle_record) {
                //Posso avviare la registrazione se ho archiviazione disponibile
                if(!isExternalStorageAvailable() || isExternalStorageReadOnly()){
                    recordOn = false;
                }

                //Posso avviare la registrazione solo se ho dei piani su cui agganciare ancore.
                if(!hasTrackingPlane()){
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
                        frameCounter = 0;
                        recordOn = true;
                    }
                }

                item.setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);

                return true;
            }

            return false;
        });

        mySurfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        mySurfaceView.setPreserveEGLContextOnPause(true);
        //Provo un contesto 3.0, che mi serve per il caricamento della maschera di float.
        mySurfaceView.setEGLContextClientVersion(3);
        mySurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 8); // Alpha used for plane blending.
        mySurfaceView.setRenderer(this);
        mySurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        mySurfaceView.setWillNotDraw(false);

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

        mySurfaceView.onResume();
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
            mySurfaceView.onPause();
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
        onSessionPause();
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
            planeRenderer.createOnGlThread(this, "models/trigrid.png");
            screenshotRenderer.createOnGlThread(this, SCREENSHOT_SIZE);
            objectRenderer.createOnGlThread(this, "models/cerchio.obj");

            ShaderUtil.checkGLError(TAG, "OnSurfaceCreated");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        displayRotationHelper.onSurfaceChanged(surfaceWidth, surfaceHeight);
        backgroundRenderer.onSurfaceChanged(surfaceWidth, surfaceHeight);
        screenshotRenderer.onSurfaceChanged(surfaceWidth, surfaceHeight);

        //Lo faccio qui dove il background renderer ha già istanziato la texture necessaria all'altro programma.
        screenshotRenderer.setSourceTextureId(backgroundRenderer.getScreenshotFrameBufferTextureId());

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

            //IMPORTANTE: fa il binding tra la texutre che andrà in background e la camera.
            session.setCameraTextureName(backgroundRenderer.getBackgroundTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            frame = session.update();
            Camera camera = frame.getCamera();

            // Get projection matrix.
            //La matrice viene generata in column-major come richiesto da openGL.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, NEAR_PLANE, FAR_PLANE);

            // Get camera matrix and draw.
            //La matrice viene generata in column-major come richiesto da openGL.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Handle one tap per frame.
            handleTap(frame, camera);

            //Disegno background
            backgroundRenderer.draw(frame);
            screenshotRenderer.drawOnPBO();

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                String trackingFailureReasonString = TrackingStateHelper.getTrackingFailureReasonString(camera);
                if(!trackingFailureReasonString.isEmpty())
                    runOnUiThread(()->showLoadingMessage(trackingFailureReasonString, Snackbar.LENGTH_LONG));

                //Devo interrompere la registrazione
                if(recordOn){
                    recordOn = false;
                    optionsMenu.findItem(R.id.toggle_record).setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);
                }

                return;
            }

            // No tracking error at this point. If we detected any plane, then hide the
            // message UI, otherwise show searchingPlane message.
            if (!hasTrackingPlane()) {
                runOnUiThread(()->showLoadingMessage(R.string.searching_plane_message, Snackbar.LENGTH_INDEFINITE));
            } else {
                runOnUiThread(this::hideLoadingMessage);
            }

            //Disegno altra roba
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            // Visualize anchors created by touch.
            for (CustomAnchor customAnchor : anchors) {
                final Anchor anchor = customAnchor.getAnchor();

                if (anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }

                //Modifico la anchorMatrix in modo che l'oggetto si muova.
                customAnchor.update();
                customAnchor.toMatrix(anchorMatrix, 0);

                objectRenderer.updateModelMatrix(anchorMatrix);
                objectRenderer.draw(viewmtx, projmtx);
            }

            //Salvataggio del frame
            if(recordOn){
                final SceneDataset dataset = SceneDataset.parseDataset(frame.getAndroidSensorPose(), camera, viewmtx, projmtx, anchors, frameCounter, System.currentTimeMillis(), screenshotRenderer.getScaledWidth(), screenshotRenderer.getScaledHeight(), NEAR_PLANE, FAR_PLANE);
                dataset.setDisplayRotation(displayRotationHelper.getDisplayRotation());

                final ByteBuffer data = screenshotRenderer.getByteBufferScreenshot().duplicate();

                if(data.limit() > 0){
//                    runInBackground(()->{
                        if(recordOn){
                            saveScene(dataset);
                            savePicture(data);
                            fileNameCounter++;
                        }
//                    });
                }
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }

        frameCounter++;
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        //Continuo solo se non sto registrando
        if(recordOn) return;

        MotionEvent tap = tapHelper.poll();

        if (tap != null) {
            if (camera.getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(tap)) {

                    // Check if any plane was hit, and if it was hit inside the plane polygon
                    Trackable trackable = hit.getTrackable();
                    // Creates an anchor if a plane or an oriented point was hit.

                    if ((trackable instanceof Plane
                            && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                            && (calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                            || (trackable instanceof Point
                            && ((Point) trackable).getOrientationMode()
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {

                        // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (anchors.size() >= 20) {
                            anchors.get(0).getAnchor().detach();
                            anchors.remove(0);
                        }

                        // Assign a color to the object for rendering.
                        Anchor anchor = hit.createAnchor();

                        anchors.add(new CustomAnchor(anchor, trackable));

                        break;
                    }
                }
            }
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private boolean hasTrackingPlane() {
        if(session != null){
            for (Plane plane : session.getAllTrackables(Plane.class)) {
                if (plane.getTrackingState() == TrackingState.TRACKING) {
                    return true;
                }
            }
        }

        return false;
    }

    // Calculate the normal distance to plane from cameraPose, the given planePose should have y axis
    // parallel to plane's normal, for example plane's center pose or hit test pose.
    public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0]
                + (cameraY - planePose.ty()) * normal[1]
                + (cameraZ - planePose.tz()) * normal[2];
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


    //https://stackoverflow.com/questions/48191513/how-to-take-picture-with-camera-using-arcore
    /**
     * Call from the GLThread to save a picture of the current frame.
     */
    public void savePicture(final ByteBuffer image) {
        final File outPicture = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + imagesPathString), fileNameCounter + ".jpg");

        // Make sure the directory exists
        File parentFile = outPicture.getParentFile();

        if(parentFile != null){
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    Log.e(TAG, "Errore creazione cartella immagini");
                    return;
                }
            }
        }else{
            Log.e(TAG, "Errore ricerca cartella immagini");
            return;
        }

        bmp.copyPixelsFromBuffer(image);

        // Write it to disk.
        try (FileOutputStream fos = new FileOutputStream(outPicture)){
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Errore salvataggio immagine", e);
        }
    }

    public void saveScene(final SceneDataset dataset){
        final File outDataset = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + scenesPathString) , fileNameCounter + ".json");

        File parentFile = outDataset.getParentFile();

        if(parentFile != null){
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    Log.e(TAG, "Errore creazione cartella dataset");
                    return;
                }
            }
        }else{
            Log.e(TAG, "Errore ricerca cartella dataset");
            return;
        }

        // Write it to disk.
        try (OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(outDataset), StandardCharsets.UTF_8)) {
            String datasetString = GSON.toJson(dataset);
            outputStream.write(datasetString);
            outputStream.flush();
        }catch (IOException ex){
            Log.e(TAG, "Errore salvataggio dataset", ex);
        }
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
