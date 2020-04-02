package it.unibo.cvlab.computescene;

import it.unibo.cvlab.computescene.dataset.SceneDataset;
import it.unibo.cvlab.computescene.model.Model;
import it.unibo.cvlab.computescene.rendering.BackgroundRenderer;
import it.unibo.cvlab.computescene.rendering.ObjectRenderer;
import it.unibo.cvlab.computescene.rendering.ScreenshotRenderer;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.system.*;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.system.MemoryUtil.NULL;

public class ComputeScene {
    private static final String TAG = ComputeScene.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(ComputeScene.class.getSimpleName());

    private static final String WINDOW_TITLE = "Compute Scene";

    // The window handle
    private long window;

    private int surfaceWidth, surfaceHeight;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer objectRenderer = new ObjectRenderer();
    private final ScreenshotRenderer screenshotRenderer = new ScreenshotRenderer();

    private DatasetLoader datasetLoader;
    private Model model;

    public ComputeScene(Model model) throws IOException {
        datasetLoader = new DatasetLoader();
        this.model = model;
    }

    public void load() throws IOException {
        //Devo ricavare info dal primo dataset
        SceneDataset sceneDataset = datasetLoader.parseDataset();

        this.surfaceWidth = sceneDataset.getWidth();
        this.surfaceHeight = sceneDataset.getHeight();


    }

    public void run() throws IOException {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");
        init();
        loop();

        // Free the window callbacks and destroy the window
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        GLFW.glfwTerminate();
        GLFWErrorCallback glfwErrorCallback = GLFW.glfwSetErrorCallback(null);
        if(glfwErrorCallback != null) glfwErrorCallback.free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !GLFW.glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        GLFW.glfwDefaultWindowHints(); // optional, the current window hints are already the default
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE); // the window will stay hidden after creation
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE); // the window will be not resizable

        // Create the window
        window = GLFW.glfwCreateWindow(surfaceWidth, surfaceHeight, WINDOW_TITLE, NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        GLFW.glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
                GLFW.glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = MemoryStack.stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            GLFW.glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());

            // Center the window
            GLFW.glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        GLFW.glfwMakeContextCurrent(window);
        // Enable v-sync
        GLFW.glfwSwapInterval(GLFW.GLFW_TRUE);

        // Make the window visible
        GLFW.glfwShowWindow(window);
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Set the clear color
        onSurfaceCreated();

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !GLFW.glfwWindowShouldClose(window) ) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            try {
                //Carico dataset e immagine
                BufferedImage image = datasetLoader.getImage();
                SceneDataset sceneDataset = datasetLoader.parseDataset();
                draw(image, sceneDataset);
            } catch (IOException e) {
                Log.log(Level.SEVERE, "Impossibile eseguire draw: "+e.getLocalizedMessage());
            }

            if(datasetLoader.hasNext()){
                datasetLoader.next();
            }else{
                GLFW.glfwSetWindowShouldClose(window, true);
            }

            GLFW.glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            GLFW.glfwPollEvents();

        }
    }

    private void onSurfaceCreated() {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        try {
            backgroundRenderer.createOnGlThread(surfaceWidth, surfaceHeight);
            objectRenderer.createOnGlThread();
            screenshotRenderer.createOnGlThread(surfaceWidth, surfaceHeight, surfaceWidth, surfaceHeight);
        } catch (IOException e) {
            Log.log(Level.SEVERE, "Impossibile caricare shaders: "+e.getLocalizedMessage());
        }

    }

    private void draw(BufferedImage image, SceneDataset sceneDataset) throws IOException {

        //Imposto l'immagine di sfondo
        backgroundRenderer.loadBackgroudImage(image);

        //Disegno lo sfondo.
        backgroundRenderer.draw();

        //Creo uno screenshot per la rete neurale.
        screenshotRenderer.drawOnPBO();
        byte[] byteArrayScreenshot = screenshotRenderer.getByteArrayScreenshot(screenshotRenderer.getScreenshotFrameBuffer());
        //Normalizzo se necessario



        //Per ogni ancora disegno l'oggetto.
    }

    public static void main(String[] args) throws IOException {
        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

        ModelLoader modelLoader = new ModelLoader();
        ComputeScene computeScene;

        System.out.println("Inserisci il direttorio del dataset:");
        String datasetPathString = inReader.readLine();

        DatasetLoader datasetLoader = new DatasetLoader();

        System.out.println("Seleziona il modello:");
        Path[] paths = modelLoader.getModelPathList().toArray(new Path[0]);
        int i = 1;
        for(Path path : paths){
            System.out.println(i + ": " + path);
            i++;
        }

        int scelta = 0;

        do {
            String tmp = inReader.readLine();

            try {
                scelta = Integer.parseInt(tmp);
            } catch (NumberFormatException e) {
                scelta = 0;
            }
        } while (scelta <= 0 || scelta > paths.length);


        Model model = modelLoader.parseModel(paths[scelta - 1]);

        //Per ogni ancora seleziona obj da utilizzare

    }

}
