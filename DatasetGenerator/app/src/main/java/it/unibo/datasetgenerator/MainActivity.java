package it.unibo.datasetgenerator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
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
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import it.unibo.datasetgenerator.save.PointCloudDataset;
import it.unibo.datasetgenerator.save.SensorData;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Gson GSON = new Gson();

    private static final int datasetPath = R.string.dataset_path;
    private static final int imagesPath = R.string.images_path;
    private static final int pointsPath = R.string.points_path;

    private static final float NEAR_PLANE  = 0.1f;
    private static final float FAR_PLANE  = 10.0f;

    @BindView(R.id.surfaceView)
    GLSurfaceView mySurfaceView;

    @BindView(R.id.optionsToolbar)
    Toolbar optionsToolbar;

    @BindView(R.id.logTextView)
    TextView logTextView;

    private Unbinder unbinder;

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
    private String datasetIdentifier;

    private int datasetCounter;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        datasetPathString = getString(datasetPath);
        imagesPathString = getString(imagesPath);
        pointsPathString = getString(pointsPath);

        //Binding tra XML e Java tramite ButterKnife
        unbinder = ButterKnife.bind(this);

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

        final EditText editTextDialog = (EditText) dialogView.findViewById(R.id.editTextDialog);

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
                        datasetCounter = 0;
                        recordOn = true;
                    }
                }

                optionsMenu.findItem(R.id.toggle_record).setIcon(recordOn ? R.drawable.ic_stop : R.drawable.ic_record);

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

        installRequested = false;

    }


    @Override
    protected void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("handlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        if(sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
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
                session = new Session(/* context= */ this);

                Config.LightEstimationMode lightEstimationMode =
                        Config.LightEstimationMode.DISABLED;

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

        mySurfaceView.onResume();
        displayRotationHelper.onResume();

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

        if(sensorManager != null)
            sensorManager.unregisterListener(this);

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

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            pointCloudRenderer.createOnGlThread(/*context=*/ this);
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

                //Da qui in poi posso eseguire in un altro thread.
                savingFrame = true;
                runInBackground(()->{
                    savePicture();
                    savePointCloud(pointCloudDataset);
                    datasetCounter++;
                    savingFrame = false;
                });
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
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
            return true;
        }
        return false;
    }

    private static boolean isExternalStorageAvailable() {
        String extStorageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
            return true;
        }
        return false;
    }

    //https://stackoverflow.com/questions/48191513/how-to-take-picture-with-camera-using-arcore
    /**
     * Call from the GLThread to save a picture of the current frame.
     */
    public void savePicture() {
        //Creo due file: uno per l'immagine e uno per i punti.
        final File outPicture = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + imagesPathString), datasetCounter + ".png");

        // Make sure the directory exists
        if (!outPicture.getParentFile().exists()) {
            if (!outPicture.getParentFile().mkdirs()) {
                Log.e(TAG, "Errore creazione cartella immagine");
                return;
            }
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
        //Creo due file: uno per l'immagine e uno per i punti.

        final File outPointCloud = new File(getExternalFilesDir(datasetPathString + datasetIdentifier + pointsPathString) , datasetCounter + ".json");

        if (!outPointCloud.getParentFile().exists()) {
            if (!outPointCloud.getParentFile().mkdirs()) {
                Log.e(TAG, "Errore creazione cartella point cloud");
                return;
            }
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
        logTextView.setText(text);
    }

}
