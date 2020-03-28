package it.unibo.cvlab.datasetgenerator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.PermissionsHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
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
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import it.unibo.cvlab.datasetgenerator.save.PointCloudDataset;
import it.unibo.cvlab.datasetgenerator.save.SensorData;
import it.unibo.cvlab.datasetgenerator.save.TOFDataset;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Gson GSON = new Gson();

    private static final int datasetPath = R.string.dataset_path;
    private static final int imagesPath = R.string.images_path;
    private static final int pointsPath = R.string.points_path;
    private static final int tofPath = R.string.tof_path;

    private static final float NEAR_PLANE  = 0.1f;
    private static final float FAR_PLANE  = 10.0f;

    @BindView(R.id.surfaceView)
    GLSurfaceView mySurfaceView;

    @BindView(R.id.optionsToolbar)
    Toolbar optionsToolbar;

    @BindView(R.id.logTextView)
    TextView logTextView;

    private boolean installRequested;
    private Session session;

    //Helper vari
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;//Usato per gestire il tocco dell'utente

    //Usati per il rendering degli oggetti virtuali:
    //Usano direttamente opengl
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    private boolean recordOn = false;

    private int surfaceWidth, surfaceHeight;
    private boolean evenFrame = true;

    private Snackbar loadingMessageSnackbar;

    private String datasetPathString;
    private String imagesPathString;
    private String pointsPathString;
    private String tofPathString;
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

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor linearAccelerometer;
    private Sensor gyroscope;
    private Sensor pose6Dof;

    private SensorData accelerometerData;
    private SensorData linearAccelerometerData;
    private SensorData gyroscopeData;
    private SensorData pose6DofData;

    private int minNumPoints = 1;

    //----------------------------------------------------------------------------------------------
    //Roba aggiuntiva per TOF Sensor
    //https://github.com/mpottinger/arcoreDepth_example

    private boolean recordTOF = false;
    private long lastTimestampTOF = -1L;

    // Whether the GL surface has been created.
    private boolean surfaceCreated;

    // ARCore shared camera instance, obtained from ARCore session that supports sharing.
    private SharedCamera sharedCamera;

    // Camera ID for the camera used by ARCore.
    private String cameraId;

    // Ensure GL surface draws only occur when new frames are available.
    private final AtomicBoolean shouldUpdateSurfaceTexture = new AtomicBoolean(false);

    public TOFImageReader TOFImageReader;
    private boolean TOFAvailable = false;

    // Camera preview capture request builder
    private CaptureRequest.Builder previewCaptureRequestBuilder;

    // Camera capture session. Used by both non-AR and AR modes.
    private CameraCaptureSession captureSession;

    // Reference to the camera system service.
    private CameraManager cameraManager;

    // Camera device. Used by both non-AR and AR modes.
    private CameraDevice cameraDevice;

    // Prevent any changes to camera capture session after CameraManager.openCamera() is called, but
    // before camera device becomes active.
    private boolean captureSessionChangesPossible = true;

    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private final ConditionVariable safeToExitApp = new ConditionVariable();

    // Looper handler thread.
    private HandlerThread backgroundThread;
    // Looper handler.
    private Handler backgroundHandler;

    // Camera device state callback.
    private final CameraDevice.StateCallback cameraDeviceCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " opened.");
                    MainActivity.this.cameraDevice = cameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onClosed(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " closed.");
                    MainActivity.this.cameraDevice = null;
                    safeToExitApp.open();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.w(TAG, "Camera device ID " + cameraDevice.getId() + " disconnected.");
                    cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    Log.e(TAG, "Camera device ID " + cameraDevice.getId() + " error " + error);
                    cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                    // Fatal error. Quit application.
                    finish();
                }
            };

    // Repeating camera capture session state callback.
    CameraCaptureSession.StateCallback cameraCaptureCallback =
            new CameraCaptureSession.StateCallback() {

                // Called when the camera capture session is first configured after the app
                // is initialized, and again each time the activity is resumed.
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session configured.");
                    captureSession = session;
                    setRepeatingCaptureRequest();
                }

                @Override
                public void onSurfacePrepared(
                        @NonNull CameraCaptureSession session, @NonNull Surface surface) {
                    Log.d(TAG, "Camera capture surface prepared.");
                }

                @Override
                public void onReady(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session ready.");
                }

                @Override
                public void onActive(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session active.");
                    resumeARCore();
                    synchronized (MainActivity.this) {
                        captureSessionChangesPossible = true;
                        MainActivity.this.notify();
                    }
                }

                @Override
                public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
                    Log.w(TAG, "Camera capture queue empty.");
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session closed.");
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure camera capture session.");
                }
            };

    // Repeating camera capture session capture callback.
    private final CameraCaptureSession.CaptureCallback captureSessionCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                    shouldUpdateSurfaceTexture.set(true);
                }

                @Override
                public void onCaptureBufferLost(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull Surface target,
                        long frameNumber) {
                    Log.e(TAG, "onCaptureBufferLost: " + frameNumber);
                }

                @Override
                public void onCaptureFailed(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureFailure failure) {
                    Log.e(TAG, "onCaptureFailed: " + failure.getFrameNumber() + " " + failure.getReason());
                }

                @Override
                public void onCaptureSequenceAborted(
                        @NonNull CameraCaptureSession session, int sequenceId) {
                    Log.e(TAG, "onCaptureSequenceAborted: " + sequenceId + " " + session);
                }
            };

    private void createCameraPreviewSession() {
        Log.d("TAG" + " createCameraPreviewSession: ", "starting camera preview session.");
        try {
            // Create an ARCore compatible capture request using `TEMPLATE_RECORD`.
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Build surfaces list, starting with ARCore provided surfaces.
            List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();
            Log.d("TAG" + " createCameraPreviewSession: ", "surfaceList: sharedCamera.getArCoreSurfaces(): " + surfaceList.size());

            // Add a CPU image reader surface. On devices that don't support CPU image access, the image
            // may arrive significantly later, or not arrive at all.
            if (TOFAvailable) surfaceList.add(TOFImageReader.imageReader.getSurface());
            // Surface list should now contain three surfaces:
            // 0. sharedCamera.getSurfaceTexture()
            // 1. …
            // 2. depthImageReader.getSurface()

            // Add ARCore surfaces and CPU image surface targets.
            for (Surface surface : surfaceList) {
                previewCaptureRequestBuilder.addTarget(surface);
            }

            // Wrap our callback in a shared camera callback.
            CameraCaptureSession.StateCallback wrappedCallback = sharedCamera.createARSessionStateCallback(cameraCaptureCallback, backgroundHandler);

            // Create camera capture session for camera preview using ARCore wrapped callback.
            cameraDevice.createCaptureSession(surfaceList, wrappedCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        }
    }

    private synchronized void waitUntilCameraCaptureSesssionIsActive() {
        while (!captureSessionChangesPossible) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Unable to wait for a safe time to make changes to the capture session", e);
            }
        }
    }

    private void resumeARCore() {
        // Ensure that session is valid before triggering ARCore resume. Handles the case where the user
        // manually uninstalls ARCore while the app is paused and then resumes.
        if (session == null) {
            return;
        }

        try {
            // Resume ARCore.
            session.resume();
            // Set capture session callback while in AR mode.
            sharedCamera.setCaptureCallback(captureSessionCallback, backgroundHandler);
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Failed to resume ARCore session", e);
        }

    }

    private void pauseARCore() {
        shouldUpdateSurfaceTexture.set(false);
        if (session != null) session.pause();
    }

    // Called when starting non-AR mode or switching to non-AR mode.
    // Also called when app starts in AR mode, or resumes in AR mode.
    private void setRepeatingCaptureRequest() {
        try {
            captureSession.setRepeatingRequest(
                    previewCaptureRequestBuilder.build(), captureSessionCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to set repeating request", e);
        }
    }

    // Start background handler thread, used to run callbacks without blocking UI thread.
    private void startBackgroundThreads() {
        backgroundThread = new HandlerThread("sharedCameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        handlerThread = new HandlerThread("handlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        TOFImageReader.startBackgroundThread();
    }

    // Stop background handler thread.
    private void stopBackgroundThreads() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while trying to join background handler thread", e);
            }
        }

        if(handlerThread != null){
            handlerThread.quitSafely();

            try {
                handlerThread.join();
                handlerThread = null;
                handler = null;
            } catch (final InterruptedException e) {
                Log.e(TAG, "Impossibile fermare handler pydnet", e);
            }
        }

        TOFImageReader.stopBackgroundThread();
    }

    // Perform various checks, then open camera device and create CPU image reader.
    private void openCamera() {
        Log.v(TAG + " opencamera: ", "Perform various checks, then open camera device and create CPU image reader.");

        // Don't open camera if already opened.
        if (cameraDevice != null) {
            return;
        }

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
                if (!PermissionsHelper.hasPermissions(this)) {
                    PermissionsHelper.requestPermissions(this);
                    return;
                }

                // Create the session.
                session = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));

                Config config = new Config(session);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                config.setFocusMode(Config.FocusMode.AUTO);
                config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                config.setCloudAnchorMode(Config.CloudAnchorMode.DISABLED);
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

        // Store the ARCore shared camera reference.
        sharedCamera = session.getSharedCamera();
        // Store the ID of the camera used by ARCore.
        cameraId = session.getCameraConfig().getCameraId();

        ArrayList<Size> resolutions = getResolutions(this, cameraId, ImageFormat.DEPTH16);
        TOFAvailable = resolutions.size() > 0;

        Log.i(TAG, "TOF is "+ (TOFAvailable ? "Available" : "Not Available"));

        if (TOFAvailable) {
            int width = 0, height = 0;
            for(Size resolution : resolutions){
                Log.v(TAG + "DEPTH16 resolution: ", resolution.toString());

                if(width < resolution.getWidth() && height < resolution.getHeight()){
                    width = resolution.getWidth();
                    height = resolution.getHeight();
                }
            }
            TOFImageReader.createImageReader(width, height);
            // When ARCore is running, make sure it also updates our CPU image surface.
            sharedCamera.setAppSurfaces(this.cameraId, Collections.singletonList(TOFImageReader.imageReader.getSurface()));
        }

        try {
            // Wrap our callback in a shared camera callback.
            CameraDevice.StateCallback wrappedCallback = sharedCamera.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler);

            // Store a reference to the camera system service.
            cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

            // Prevent app crashes due to quick operations on camera open / close by waiting for the
            // capture session's onActive() callback to be triggered.
            captureSessionChangesPossible = false;

            // Open the camera device using the ARCore wrapped callback.
            if(cameraManager != null)
                cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }


    // Close the camera device.
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            waitUntilCameraCaptureSesssionIsActive();
            safeToExitApp.close();
            cameraDevice.close();
            safeToExitApp.block();
        }

        if (TOFImageReader.imageReader != null) {
            TOFImageReader.imageReader.close();
            TOFImageReader.imageReader = null;
        }
    }

    public ArrayList<Size> getResolutions (Context context, String cameraId, int imageFormat){
        Log.v(TAG + "getResolutions:", " cameraId:" + cameraId + " imageFormat: " + imageFormat);

        ArrayList<Size> output = new ArrayList<>();
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            if(manager != null){
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if(streamConfigurationMap != null){
                    Size[] outputSizes = streamConfigurationMap.getOutputSizes(imageFormat);

                    if(outputSizes != null){
                        for (Size s : outputSizes) {
                            output.add(new Size(s.getWidth(), s.getHeight()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    //----------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        datasetPathString = getString(datasetPath);
        imagesPathString = getString(imagesPath);
        pointsPathString = getString(pointsPath);
        tofPathString = getString(tofPath);

        //Binding tra XML e Java tramite ButterKnife
        ButterKnife.bind(this);

        setNumPointsLog(0);

        //Ricerca dei sensori
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager != null){
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            pose6Dof = sensorManager.getDefaultSensor(Sensor.TYPE_POSE_6DOF);
        }

        //Impostazione dialogo
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.edit_text_dialog, null);

        final EditText editTextDialog = dialogView.findViewById(R.id.editTextDialog);

        final AlertDialog dialogBuilder = new AlertDialog.Builder(this).setMessage(R.string.dialog_message)
                .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> {
            try{
                int tmp = Integer.parseInt(editTextDialog.getText().toString());
                if(tmp >= 0){
                    minNumPoints = tmp;
                }
            }catch (NumberFormatException ex){
                showLoadingMessage(R.string.dialog_set_num_point_failed, Snackbar.LENGTH_SHORT);
            }
        }).setNegativeButton(R.string.dialog_cancel, (dialogInterface, i) -> {}).create();

        dialogBuilder.setView(dialogView);


        //Impostazione menu
        Menu optionsMenu = optionsToolbar.getMenu();
        optionsMenu.findItem(R.id.toggle_record).setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);
        optionsMenu.findItem(R.id.toggle_tof).setIcon(recordTOF ? R.drawable.ic_tof_on : R.drawable.ic_tof_off);

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

            if(item.getItemId() == R.id.toggle_tof){
                //Possibile solo se non sto registrando e ho il sensore
                if(!recordOn && TOFAvailable){
                    recordTOF = ! recordTOF;
                    item.setIcon(recordTOF ? R.drawable.ic_tof_on : R.drawable.ic_tof_off);
                }

                return true;
            }

            if (item.getItemId() == R.id.showSetNumPointDialog) {
                dialogBuilder.show();
                return true;
            }

            return false;
        });

        //Helpers
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        //Impostazioni opengl
        // Set up tap listener.
        tapHelper = new TapHelper(this);
        mySurfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        mySurfaceView.setPreserveEGLContextOnPause(true);
        //Provo un contesto 3.0, che mi serve per il caricamento della maschera di float.
        mySurfaceView.setEGLContextClientVersion(3);
        mySurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 8); // Alpha used for plane blending.
        mySurfaceView.setRenderer(this);
        mySurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        mySurfaceView.setWillNotDraw(false);

        TOFImageReader = new TOFImageReader();

        installRequested = false;

    }


    @Override
    protected void onResume() {
        super.onResume();

        waitUntilCameraCaptureSesssionIsActive();
        startBackgroundThreads();

        mySurfaceView.onResume();

        // When the activity starts and resumes for the first time, openCamera() will be called
        // from onSurfaceCreated(). In subsequent resumes we call openCamera() here.
        if (surfaceCreated) {
            openCamera();
        }

        displayRotationHelper.onResume();

        if(sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        mySurfaceView.onPause();
        waitUntilCameraCaptureSesssionIsActive();
        displayRotationHelper.onPause();
        pauseARCore();
        closeCamera();
        stopBackgroundThreads();

        if(sensorManager != null)
            sensorManager.unregisterListener(this);

        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!PermissionsHelper.hasPermissions(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!PermissionsHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                PermissionsHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    //Metodo per ricavare i dati dai sensori
    //https://stackoverflow.com/questions/36106848/android-getting-data-simultaneously-from-accelerometer-and-gyroscope
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(gyroscope != null && gyroscope.getName().equals(sensorEvent.sensor.getName())){
            gyroscopeData = SensorData.getFromSensorEvent(sensorEvent);
        }

        if(accelerometer != null && accelerometer.getName().equals(sensorEvent.sensor.getName())){
            accelerometerData = SensorData.getFromSensorEvent(sensorEvent);
        }

        if(linearAccelerometer != null && linearAccelerometer.getName().equals(sensorEvent.sensor.getName())){
            linearAccelerometerData = SensorData.getFromSensorEvent(sensorEvent);
        }

        if(pose6Dof != null && pose6Dof.getName().equals(sensorEvent.sensor.getName())){
            pose6DofData = SensorData.getFromSensorEvent(sensorEvent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //Non mi serve...
    }

    //Metodi per il draw opengl

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        surfaceCreated = true;

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            openCamera();
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

        if (TOFAvailable && TOFImageReader.frameCount == 0) return;

        if (!shouldUpdateSurfaceTexture.get()) {
            // Not ready to draw.
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

            //Qui inizia il disegno dello sfondo (camera)
            // If frame is ready, render camera preview image to the GL surface
            backgroundRenderer.draw(frame);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                String trackingFailureReasonString = TrackingStateHelper.getTrackingFailureReasonString(camera);
                if(!trackingFailureReasonString.isEmpty())
                    runOnUiThread(()->showLoadingMessage(trackingFailureReasonString, Snackbar.LENGTH_LONG));
                return;
            }

            //Faccio il rendering dei punti solo se ho acceso la registrazione e il frame è dispari.
            //Faccio così perchè nell'immagine salvata non voglio vedere i puntini.
            if (evenFrame && recordOn && !savingFrame) {
                //Queste sono le operazioni che devo fare in contemporanea con il GL thread.
                PointCloudDataset pointCloudDataset;

                try (PointCloud pointCloud = frame.acquirePointCloud()) {
                    if (pointCloud.getTimestamp() == lastTimestamp) {
                        // Redundant call.
                        return;
                    }

                    int numPoints = PointCloudDataset.getNumPoints(pointCloud);

                    if(numPoints < minNumPoints){
                        //Non ho abbastanza punti
                        return;
                    }

                    lastTimestamp = pointCloud.getTimestamp();

                    pointCloudDataset = PointCloudDataset.parseDataset(pointCloud, frame.getAndroidSensorPose(), camera, surfaceWidth, surfaceHeight, NEAR_PLANE, FAR_PLANE);
                    pointCloudDataset.setAccelerometerData(accelerometerData);
                    pointCloudDataset.setGyroscopeData(gyroscopeData);
                    pointCloudDataset.setLinearAccelerometerData(linearAccelerometerData);
                    pointCloudDataset.setPose6DofData(pose6DofData);
                    pointCloudDataset.setDisplayRotation(displayRotationHelper.getDisplayRotation());
                }

                // Read the pixels from the current GL frame.
                pixelDataBuffer.position(0);
                GLES20.glReadPixels(0, 0, surfaceWidth, surfaceHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelDataBuffer);

                //Lettura sensore TOF solo se disponibile
                if(recordTOF && TOFAvailable){
                    if(TOFImageReader.timestamp == lastTimestampTOF){
                        return;
                    }

                    lastTimestampTOF = TOFImageReader.timestamp;

                    final TOFDataset tofDataset = TOFDataset.parseDataset(TOFImageReader);
                    //Da qui in poi posso eseguire in un altro thread.
                    savingFrame = true;
                    runInBackground(()->{
                        savePicture();
                        savePointCloud(pointCloudDataset);
                        saveTofDataset(tofDataset);
                        fileNameCounter++;
                        savingFrame = false;
                    });
                }else{
                    //Da qui in poi posso eseguire in un altro thread.
                    savingFrame = true;
                    runInBackground(()->{
                        savePicture();
                        savePointCloud(pointCloudDataset);
                        fileNameCounter++;
                        savingFrame = false;
                    });
                }
            } else {
                // Get projection matrix.
                float[] projmtx = new float[16];
                camera.getProjectionMatrix(projmtx, 0, NEAR_PLANE, FAR_PLANE);

                // Get camera matrix and draw.
                float[] viewmtx = new float[16];
                camera.getViewMatrix(viewmtx, 0);

                // Compute lighting from average intensity of the image.
                // The first three components are color scaling factors.
                // The last one is the average pixel intensity in gamma space.
                final float[] colorCorrectionRgba = new float[4];
                frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

                //Qui inizia il disegno 3D.

                // Visualize tracked points.
                // Use try-with-resources to automatically release the point cloud.
                try (PointCloud pointCloud = frame.acquirePointCloud()) {
                    int numPoints = PointCloudDataset.getNumPoints(pointCloud);
                    setNumPointsLog(numPoints);
                    pointCloudRenderer.update(pointCloud);
                    pointCloudRenderer.draw(viewmtx, projmtx);
                }
            }

            evenFrame = !evenFrame;
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    //https://github.com/FilippoAleotti/mobilePydnet/
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
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
    public void savePicture() {
        final File outPicture = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + imagesPathString), fileNameCounter + ".png");

        // Make sure the directory exists
        File parentFile = outPicture.getParentFile();

        if(parentFile != null){
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    Log.e(TAG, "Errore creazione cartella immagine");
                    return;
                }
            }
        }else{
            Log.e(TAG, "Errore ricerca cartella immagine");
            return;
        }

        // Convert the pixel data from RGBA to what Android wants, ARGB.
        for (int i = 0; i < surfaceHeight; i++) {
            for (int j = 0; j < surfaceWidth; j++) {
                int p = pixelData[i * surfaceWidth + j];
                int b = (p & 0x00ff0000) >> 16;
                int r = (p & 0x000000ff) << 16;
                int ga = p & 0xff00ff00;
                bitmapData[(surfaceHeight - i - 1) * surfaceWidth + j] = ga | r | b;
            }
        }

        // Create a bitmap.
        Bitmap bmp = Bitmap.createBitmap(bitmapData,
                surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);

        // Write it to disk.
        try (FileOutputStream fos = new FileOutputStream(outPicture)){
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Errore salvataggio immagine", e);
        }
    }

    public void savePointCloud(final PointCloudDataset dataset){
        final File outPointCloud = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + pointsPathString) , fileNameCounter + ".json");

        File parentFile = outPointCloud.getParentFile();

        if(parentFile != null){
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    Log.e(TAG, "Errore creazione cartella point cloud");
                    return;
                }
            }
        }else{
            Log.e(TAG, "Errore ricerca cartella point cloud");
            return;
        }


        // Write it to disk.
        try (OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(outPointCloud), StandardCharsets.UTF_8)) {
            String datasetString = GSON.toJson(dataset);
            outputStream.write(datasetString);
            outputStream.flush();
        }catch (IOException ex){
            Log.e(TAG, "Errore salvataggio point cloud", ex);
        }
    }

    public void setNumPointsLog(int numPoints){
        String text = getString(R.string.log_numPoints, numPoints, minNumPoints);
        runOnUiThread(()->logTextView.setText(text));
    }

    public void saveTofDataset(final TOFDataset dataset){
        final File outTOF = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + tofPathString) , fileNameCounter + ".json");

        File parentFile = outTOF.getParentFile();

        if(parentFile != null){
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    Log.e(TAG, "Errore creazione cartella tof");
                    return;
                }
            }
        }else{
            Log.e(TAG, "Errore ricerca cartella tof");
            return;
        }

        // Write it to disk.
        try (OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(outTOF), StandardCharsets.UTF_8)) {
            String datasetString = GSON.toJson(dataset);
            outputStream.write(datasetString);
            outputStream.flush();
        }catch (IOException ex){
            Log.e(TAG, "Errore salvataggio tof", ex);
        }
    }

}
