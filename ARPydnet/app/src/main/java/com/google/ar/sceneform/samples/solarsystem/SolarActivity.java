/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.solarsystem;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import it.unibo.cvlab.arpydnet.R;
import it.unibo.cvlab.pydnet.ColorMapper;
import it.unibo.cvlab.pydnet.ImageUtils;
import it.unibo.cvlab.pydnet.Model;
import it.unibo.cvlab.pydnet.ModelFactory;
import it.unibo.cvlab.pydnet.Utils;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore and Sceneform APIs.
 */
public class SolarActivity extends AppCompatActivity {

    private static final String TAG = SolarActivity.class.getSimpleName();

    private static final int RC_PERMISSIONS = 0x123;
    private boolean cameraPermissionRequested;

    private GestureDetector gestureDetector;
    private Snackbar loadingMessageSnackbar = null;

    private ArSceneView arSceneView;

    private ModelRenderable sunRenderable;
    private ModelRenderable mercuryRenderable;
    private ModelRenderable venusRenderable;
    private ModelRenderable earthRenderable;
    private ModelRenderable lunaRenderable;
    private ModelRenderable marsRenderable;
    private ModelRenderable jupiterRenderable;
    private ModelRenderable saturnRenderable;
    private ModelRenderable uranusRenderable;
    private ModelRenderable neptuneRenderable;
    private ViewRenderable solarControlsRenderable;

    private final SolarSettings solarSettings = new SolarSettings();

    // True once scene is loaded
    private boolean hasFinishedLoading = false;

    // True once the scene has been placed.
    private boolean hasPlacedSolarSystem = false;

    // Astronomical units to meters ratio. Used for positioning the planets of the solar system.
    private static final float AU_TO_METERS = 0.5f;

    //Custom

    //Bitmaps
    private Bitmap depthColorBitmap = Bitmap.createBitmap(RESOLUTION.getWidth(), RESOLUTION.getHeight(), Bitmap.Config.ARGB_8888);

    //Ricavo il numero di thread massimi.
    private static int NUMBER_THREADS = Runtime.getRuntime().availableProcessors();

    //Oggetti per la pydnet
    private ModelFactory modelFactory;
    private Model currentModel;
    private FloatBuffer inference;

    //Usato per far girare pydnet in un altro thread.
    private Handler inferenceHandler;
    private HandlerThread inferenceHandlerThread;

    //Usato per indicare che la rete neurale sta effettuando una conversione.
    private volatile boolean isProcessingFrame = false;

    //Qui parametri fissi della pydnet: dipendono dal modello caricato.
    private static Utils.Resolution RESOLUTION = Utils.Resolution.RES4;
    private static Utils.Scale SCALE = Utils.Scale.HALF;
    private static float MAPPER_SCALE_FACTOR = 0.2f;
    private static float COLOR_SCALE_FACTOR = 10.5f;

    //Oggetti usati per l'unione della Pydnet e ARCore
    //Immagine dalla camera: usata per la maschera e per la pydnet.
    //Usato per generare il depth color.
    private ColorMapper colorMapper = null;


    private boolean depthColorEnabled = false;
    private boolean maskEnabled = false;
    private boolean quantizeMaskEnabled = false;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!DemoUtils.checkIsSupportedDeviceOrFinish(this)) {
            // Not a supported device.
            return;
        }

        setContentView(R.layout.activity_solar);
        arSceneView = findViewById(R.id.ar_scene_view);

        Scene scene = arSceneView.getScene();
        scene.addOnUpdateListener(this::onSceneUpdate);

        //Pydnet: ricavo il modello.
        modelFactory = new ModelFactory(getApplicationContext());
        currentModel = modelFactory.getModel(0);
        currentModel.prepare(RESOLUTION);

        colorMapper = new ColorMapper(COLOR_SCALE_FACTOR, true, NUMBER_THREADS);
        colorMapper.prepare(RESOLUTION);

//        int[] tmp = new int[RESOLUTION.getWidth() * RESOLUTION.getHeight()];
//
//        depthColorBitmap.setPixels(tmp, 0, RESOLUTION.getWidth(), 0,0,RESOLUTION.getWidth(), RESOLUTION.getHeight());
//
//        Texture.Sampler sampler =
//                Texture.Sampler.builder()
//                        .setMinFilter(Texture.Sampler.MinFilter.LINEAR)
//                        .setWrapMode(Texture.Sampler.WrapMode.REPEAT)
//                        .build();
//
//        Texture.builder()
//                .setSource(depthColorBitmap)
//                .setSampler(sampler)
//                .build()
//                .thenAccept(texture -> {
//                    arSceneView.getPlaneRenderer()
//                            .getMaterial().thenAccept(material ->
//                            material.setTexture(PlaneRenderer.MATERIAL_TEXTURE, texture));
//                });

        // Build all the planet models.
        CompletableFuture<ModelRenderable> sunStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Sol.sfb")).build();
        CompletableFuture<ModelRenderable> mercuryStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Mercury.sfb")).build();
        CompletableFuture<ModelRenderable> venusStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Venus.sfb")).build();
        CompletableFuture<ModelRenderable> earthStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Earth.sfb")).build();
        CompletableFuture<ModelRenderable> lunaStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Luna.sfb")).build();
        CompletableFuture<ModelRenderable> marsStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Mars.sfb")).build();
        CompletableFuture<ModelRenderable> jupiterStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Jupiter.sfb")).build();
        CompletableFuture<ModelRenderable> saturnStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Saturn.sfb")).build();
        CompletableFuture<ModelRenderable> uranusStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Uranus.sfb")).build();
        CompletableFuture<ModelRenderable> neptuneStage =
                ModelRenderable.builder().setSource(this, Uri.parse("sceneform/Neptune.sfb")).build();

        // Build a renderable from a 2D View.
        CompletableFuture<ViewRenderable> solarControlsStage =
                ViewRenderable.builder().setView(this, R.layout.solar_controls).build();

        CompletableFuture.allOf(
                sunStage,
                mercuryStage,
                venusStage,
                earthStage,
                lunaStage,
                marsStage,
                jupiterStage,
                saturnStage,
                uranusStage,
                neptuneStage,
                solarControlsStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                DemoUtils.displayError(this, "Unable to load renderable", throwable);
                                return null;
                            }

                            try {
                                sunRenderable = sunStage.get();
                                mercuryRenderable = mercuryStage.get();
                                venusRenderable = venusStage.get();
                                earthRenderable = earthStage.get();
                                lunaRenderable = lunaStage.get();
                                marsRenderable = marsStage.get();
                                jupiterRenderable = jupiterStage.get();
                                saturnRenderable = saturnStage.get();
                                uranusRenderable = uranusStage.get();
                                neptuneRenderable = neptuneStage.get();
                                solarControlsRenderable = solarControlsStage.get();

                                // Everything finished loading successfully.
                                hasFinishedLoading = true;

                            } catch (InterruptedException | ExecutionException ex) {
                                DemoUtils.displayError(this, "Unable to load renderable", ex);
                            }

                            return null;
                        });

        // Set up a tap gesture detector.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });

        // Set a touch listener on the Scene to listen for taps.
        arSceneView
                .getScene()
                .setOnTouchListener(
                        (HitTestResult hitTestResult, MotionEvent event) -> {
                            // If the solar system hasn't been placed yet, detect a tap and then check to see if
                            // the tap occurred on an ARCore plane to place the solar system.
                            if (!hasPlacedSolarSystem) {
                                return gestureDetector.onTouchEvent(event);
                            }

                            // Otherwise return false so that the touch event can propagate to the scene.
                            return false;
                        });

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (loadingMessageSnackbar == null) {
                                return;
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                if (plane.getTrackingState() == TrackingState.TRACKING) {
                                    hideLoadingMessage();
                                }
                            }
                        });

        // Lastly request CAMERA permission which is required by ARCore.
        DemoUtils.requestCameraPermission(this, RC_PERMISSIONS);
    }

    @Override
    protected void onResume() {
        super.onResume();

        inferenceHandlerThread = new HandlerThread("inference");
        inferenceHandlerThread.start();
        inferenceHandler = new Handler(inferenceHandlerThread.getLooper());

        if (arSceneView == null) {
            return;
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Config.LightEstimationMode lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR;
                Session session =
                        cameraPermissionRequested
                                ? DemoUtils.createArSessionWithInstallRequest(this, lightEstimationMode)
                                : DemoUtils.createArSessionNoInstallRequest(this, lightEstimationMode);
                if (session == null) {
                    cameraPermissionRequested = DemoUtils.hasCameraPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                DemoUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            DemoUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
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
        if (arSceneView != null) {
            arSceneView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (arSceneView != null) {
            arSceneView.destroy();
        }

        try {
            if (colorMapper != null)
                colorMapper.dispose();
        } catch (InterruptedException e) {
            Log.d(TAG, "Thread Interrotto nel join", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!DemoUtils.hasCameraPermission(this)) {
            if (!DemoUtils.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                DemoUtils.launchPermissionSettings(this);
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onSingleTap(MotionEvent tap) {
        if (!hasFinishedLoading) {
            // We can't do anything yet.
            return;
        }

        Frame frame = arSceneView.getArFrame();
        if (frame != null) {
            if (!hasPlacedSolarSystem && tryPlaceSolarSystem(tap, frame)) {
                hasPlacedSolarSystem = true;
            }
        }
    }

    private boolean tryPlaceSolarSystem(MotionEvent tap, Frame frame) {
        if (tap != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    // Create the Anchor.
                    Anchor anchor = hit.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arSceneView.getScene());
                    Node solarSystem = createSolarSystem();
                    anchorNode.addChild(solarSystem);
                    return true;
                }
            }
        }

        return false;
    }

    private Node createSolarSystem() {
        Node base = new Node();

        Node sun = new Node();
        sun.setParent(base);
        sun.setLocalPosition(new Vector3(0.0f, 0.5f, 0.0f));

        Node sunVisual = new Node();
        sunVisual.setParent(sun);
        sunVisual.setRenderable(sunRenderable);
        sunVisual.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));

        Node solarControls = new Node();
        solarControls.setParent(sun);
        solarControls.setRenderable(solarControlsRenderable);
        solarControls.setLocalPosition(new Vector3(0.0f, 0.25f, 0.0f));

        View solarControlsView = solarControlsRenderable.getView();
        SeekBar orbitSpeedBar = solarControlsView.findViewById(R.id.orbitSpeedBar);
        orbitSpeedBar.setProgress((int) (solarSettings.getOrbitSpeedMultiplier() * 10.0f));
        orbitSpeedBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float ratio = (float) progress / (float) orbitSpeedBar.getMax();
                        solarSettings.setOrbitSpeedMultiplier(ratio * 10.0f);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        SeekBar rotationSpeedBar = solarControlsView.findViewById(R.id.rotationSpeedBar);
        rotationSpeedBar.setProgress((int) (solarSettings.getRotationSpeedMultiplier() * 10.0f));
        rotationSpeedBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float ratio = (float) progress / (float) rotationSpeedBar.getMax();
                        solarSettings.setRotationSpeedMultiplier(ratio * 10.0f);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        // Toggle the solar controls on and off by tapping the sun.
        sunVisual.setOnTapListener(
                (hitTestResult, motionEvent) -> solarControls.setEnabled(!solarControls.isEnabled()));

        createPlanet("Mercury", sun, 0.4f, 47f, mercuryRenderable, 0.019f, 0.03f);

        createPlanet("Venus", sun, 0.7f, 35f, venusRenderable, 0.0475f, 2.64f);

        Node earth = createPlanet("Earth", sun, 1.0f, 29f, earthRenderable, 0.05f, 23.4f);

        createPlanet("Moon", earth, 0.15f, 100f, lunaRenderable, 0.018f, 6.68f);

        createPlanet("Mars", sun, 1.5f, 24f, marsRenderable, 0.0265f, 25.19f);

        createPlanet("Jupiter", sun, 2.2f, 13f, jupiterRenderable, 0.16f, 3.13f);

        createPlanet("Saturn", sun, 3.5f, 9f, saturnRenderable, 0.1325f, 26.73f);

        createPlanet("Uranus", sun, 5.2f, 7f, uranusRenderable, 0.1f, 82.23f);

        createPlanet("Neptune", sun, 6.1f, 5f, neptuneRenderable, 0.074f, 28.32f);

        return base;
    }

    private Node createPlanet(
            String name,
            Node parent,
            float auFromParent,
            float orbitDegreesPerSecond,
            ModelRenderable renderable,
            float planetScale,
            float axisTilt) {
        // Orbit is a rotating node with no renderable positioned at the sun.
        // The planet is positioned relative to the orbit so that it appears to rotate around the sun.
        // This is done instead of making the sun rotate so each planet can orbit at its own speed.
        RotatingNode orbit = new RotatingNode(solarSettings, true, false, 0);
        orbit.setDegreesPerSecond(orbitDegreesPerSecond);
        orbit.setParent(parent);

        // Create the planet and position it relative to the sun.
        Planet planet =
                new Planet(
                        this, name, planetScale, orbitDegreesPerSecond, axisTilt, renderable, solarSettings);
        planet.setParent(orbit);
        planet.setLocalPosition(new Vector3(auFromParent * AU_TO_METERS, 0.0f, 0.0f));

        return planet;
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        SolarActivity.this.findViewById(android.R.id.content),
                        R.string.plane_finding,
                        Snackbar.LENGTH_INDEFINITE);
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


    //Custom

    //https://github.com/FilippoAleotti/mobilePydnet/
    protected synchronized void runInBackground(final Runnable r) {
        if (inferenceHandler != null) {
            inferenceHandler.post(r);
        }
    }

    //https://github.com/FilippoAleotti/mobilePydnet/
    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
//                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    //https://github.com/FilippoAleotti/mobilePydnet/
    private Bitmap convertAndCrop(Image image, Utils.Resolution resolution) {
        Image.Plane[] planes = image.getPlanes();

        final byte[][] yuvBytes = new byte[3][];
        fillBytes(planes, yuvBytes);

        final int yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();

        int[] rgbInts = new int[image.getWidth() * image.getHeight()];

        ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                image.getWidth(),
                image.getHeight(),
                yRowStride,
                uvRowStride,
                uvPixelStride,
                rgbInts);

        Bitmap bmp = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        bmp.setPixels(rgbInts, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

//        Bitmap croppedBmp = Bitmap.createBitmap(bmp, 0, 0, resolution.getWidth(), resolution.getHeight());
        Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, resolution.getWidth(), resolution.getHeight(), true);

        bmp.recycle();

        return scaledBmp;
    }

    public void onSceneUpdate(FrameTime frameTime) {
        if (arSceneView.getSession() == null) {
            return;
        }

        Frame frame = arSceneView.getArFrame();

        if (frame == null) {
            return;
        }

        // Copy the camera stream to a bitmap
        try (Image image = frame.acquireCameraImage()) {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException(
                        "Expected image in YUV_420_888 format, got format " + image.getFormat());
            }

            //Posso fare la trasformazione in rgb e darlo a pydnet.
//            Bitmap pydnetImage = convertAndCrop(image, RESOLUTION);
//            currentModel.loadInput(pydnetImage);
//            pydnetImage.recycle();
//
//            //Posso far partire il modello.
//            isProcessingFrame = true;
//
//            runInBackground(() -> {
//                inference = currentModel.doInference(SCALE);
//                colorMapper.getColorMap(depthColorBitmap, inference, NUMBER_THREADS);
//                isProcessingFrame = false;
//            });

        } catch (Exception e) {
            Log.e(TAG, "Exception copying image", e);
        }
    }



}