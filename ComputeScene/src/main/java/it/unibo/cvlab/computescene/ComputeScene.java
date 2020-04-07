package it.unibo.cvlab.computescene;

import de.javagl.obj.Obj;
import it.unibo.cvlab.computescene.dataset.Pose;
import it.unibo.cvlab.computescene.dataset.SceneDataset;
import it.unibo.cvlab.computescene.loader.DatasetLoader;
import it.unibo.cvlab.computescene.loader.ModelLoader;
import it.unibo.cvlab.computescene.loader.ObjectLoader;
import it.unibo.cvlab.computescene.model.Model;
import it.unibo.cvlab.computescene.rendering.BackgroundRenderer;
import it.unibo.cvlab.computescene.rendering.ObjectRenderer;
import it.unibo.cvlab.computescene.rendering.ScreenshotRenderer;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.*;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
    private final ScreenshotRenderer inferenceRenderer = new ScreenshotRenderer();

    private final Calibrator calibrator = new Calibrator();

    private DatasetLoader datasetLoader;
    private Model model;
    private Obj obj;
    private BufferedImage texture;

    private Session session;
    private float[] inferenceArray;
    private ScreenshotRenderer.ColorType colorType;

    private Path depthPath;
    private Path omaPath;

    private ColorMapper colorMapper = new ColorMapper(Utils.PLASMA_FACTOR, 4);

    public ComputeScene(DatasetLoader datasetLoader, Path depthPath, Path omaPath, Model model, Obj obj, BufferedImage texture, ScreenshotRenderer.ColorType colorType) {
        this.datasetLoader = datasetLoader;
        this.depthPath = depthPath;
        this.omaPath = omaPath;
        this.model = model;
        this.obj = obj;
        this.texture = texture;
        this.colorType = colorType;
    }

    public void load() throws IOException {
        //Devo ricavare info dal primo dataset
        SceneDataset sceneDataset = datasetLoader.parseDataset();

        this.surfaceWidth = sceneDataset.getWidth();
        this.surfaceHeight = sceneDataset.getHeight();

        inferenceArray = new float[model.calculateOutputBufferSize(false)];

        colorMapper.prepare(model.getOutputWidth(), model.getOutputHeight());

        //Inizializzo il modello
        Graph graphDef = model.getGraphDef(model.getModelsPath());
        session = new Session(graphDef);
    }

    public void run()  {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");
        init();
        loop();

        try {
            colorMapper.dispose();
        } catch (InterruptedException e) {
            //e vabbe
        }

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
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
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
        // Disable v-sync
        GLFW.glfwSwapInterval(GLFW.GLFW_FALSE);

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
            GL30.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT); // clear the framebuffer

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
        GL30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        try {
            backgroundRenderer.createOnGlThread(surfaceWidth, surfaceHeight);
            objectRenderer.createOnGlThread(obj, texture);
            screenshotRenderer.createOnGlThread(ScreenshotRenderer.ColorType.RGBA8, surfaceWidth, surfaceHeight, surfaceWidth, surfaceHeight);
            inferenceRenderer.createOnGlThread(colorType, surfaceWidth, surfaceHeight, model.getInputWidth(), model.getInputHeight());
        } catch (IOException e) {
            Log.log(Level.SEVERE, "Impossibile caricare shaders: "+e.getLocalizedMessage());
        }

    }

    private void draw(BufferedImage backgroudImage, SceneDataset sceneDataset) throws IOException {
        System.out.println("Frame corrente:"+sceneDataset.getFrameNumber());

        //Binding dell'inference renderer
        inferenceRenderer.setSourceTextureId(backgroundRenderer.getScreenshotFrameBufferTextureId());

        //Imposto l'immagine di sfondo
        backgroundRenderer.loadBackgroudImage(backgroudImage);

        //Disegno lo sfondo.
        backgroundRenderer.draw();

        //Creo uno screenshot per la rete neurale.
        inferenceRenderer.drawOnPBO();

        Tensor<?> inputTensor;

        //Normalizzo se necessario
        if(model.isNormalizationNeeded()){
            float[] floatArrayScreenshot = inferenceRenderer.getFloatArrayScreenshot(inferenceRenderer.getScreenshotFrameBuffer());
            inputTensor = Tensor.create(model.getInputShapeLong(), FloatBuffer.wrap(floatArrayScreenshot));
        }else{
            ByteBuffer byteBufferScreenshot = inferenceRenderer.getByteBufferScreenshot(inferenceRenderer.getScreenshotFrameBuffer());
            inputTensor = Tensor.create(Byte.class, model.getInputShapeLong(), byteBufferScreenshot);
        }

        Session.Runner runner = session.runner();
        runner = runner.feed(model.getDefaultInputNode(), inputTensor);
        runner = runner.fetch(model.getDefaultOutputNode());
        List<Tensor<?>> run = runner.run();
        Tensor<?> outputTensor = run.get(0);
        FloatBuffer inference = FloatBuffer.wrap(inferenceArray);
        outputTensor.writeTo(inference);
        inputTensor.close();
        outputTensor.close();

        calibrator.setCameraPerspective(sceneDataset.getProjmtx());
        calibrator.setCameraView(sceneDataset.getViewmtx());
        calibrator.setDisplayRotation(90);//Devo settarla di default perchè qui lo schermo non ruota.

        //Salvo il depth
        BufferedImage colorMap = colorMapper.getColorMap(inference, 4);
        OutputStream outputStreamDepth = Files.newOutputStream(depthPath.resolve(sceneDataset.getFrameNumber() + ".jpg"));
        ImageIO.write(colorMap, "jpg", outputStreamDepth);
        outputStreamDepth.close();

        objectRenderer.loadInference(inferenceArray, model.getOutputWidth(), model.getOutputHeight());
        objectRenderer.setMaskEnabled(true);
        objectRenderer.setObjScaleFactor(0.5f);

        //Faccio il rendering degli oggetti.
        objectRenderer.setCameraPose(sceneDataset.getCameraPose());
        Pose[] ancore = sceneDataset.getAncore();

        for (Pose ancora : ancore){
            calibrator.calibrateScaleFactor(inference, model.getOutputWidth(), model.getOutputHeight(), ancora, sceneDataset.getCameraPose());
            objectRenderer.setScaleFactor(calibrator.getScaleFactor());
            objectRenderer.updateModelMatrix(ancora.getModelMatrix());
            objectRenderer.draw(sceneDataset.getViewmtx(), sceneDataset.getProjmtx());
        }

        //Salvo il rendering
        ByteBuffer pixelsBuffer = screenshotRenderer.getByteBufferScreenshot(screenshotRenderer.getDefaultFrameBuffer());
        BufferedImage screenshot = new BufferedImage(surfaceWidth, surfaceHeight, BufferedImage.TYPE_INT_RGB);
        int bpp = screenshotRenderer.getColorType().getByteSize();

        for(int x = 0; x < surfaceWidth; x++)
        {
            for(int y = 0; y < surfaceHeight; y++)
            {
                int i = (x + (surfaceWidth * y)) * bpp;
                int r = pixelsBuffer.get(i) & 0xFF;
                int g = pixelsBuffer.get(i + 1) & 0xFF;
                int b = pixelsBuffer.get(i + 2) & 0xFF;
                screenshot.setRGB(x, surfaceHeight - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        OutputStream outputStreamScreenshot = Files.newOutputStream(omaPath.resolve(sceneDataset.getFrameNumber() + ".jpg"));
        ImageIO.write(screenshot, "jpg", outputStreamScreenshot);
        outputStreamScreenshot.close();
    }

    private static <T> T requestInput(T[] objs, BufferedReader inReader) throws IOException {
        int i = 1;
        for(T path : objs){
            System.out.println(i + ": " + path);
            i++;
        }

        int scelta = 0;

        do {
            String tmp = inReader.readLine();

            if(tmp == null) System.exit(0);

            try {
                scelta = Integer.parseInt(tmp);
            } catch (NumberFormatException e) {
                scelta = 0;
            }
        } while (scelta <= 0 || scelta > objs.length);

        return objs[scelta - 1];
    }

    public static void main(String[] args) throws IOException {
        //prendere in input i delta

        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Scansione modelli e oggetti...");

        ModelLoader modelLoader = new ModelLoader();
        ObjectLoader objectLoader = new ObjectLoader();
        ComputeScene computeScene;

        System.out.println("Inserisci il direttorio del dataset:");
        String datasetPathString = inReader.readLine();

        if(datasetPathString == null) System.exit(0);

        DatasetLoader datasetLoader = new DatasetLoader(datasetPathString);

        Path imagesPath = datasetLoader.getImagesPath();
        Path posesPath = datasetLoader.getPosesPath();

        Path depthPath = imagesPath.resolve("depth");

        if(!Files.isDirectory(depthPath))
            Files.createDirectories(depthPath);

        Path omaPath = imagesPath.resolve("oma");

        if(!Files.isDirectory(omaPath))
            Files.createDirectories(omaPath);

        System.out.println("Images path: "+imagesPath);
        System.out.println("Poses path: "+posesPath);
        System.out.println("Depth path: "+depthPath);
        System.out.println("OMA path: "+omaPath);
        System.out.println("Numero frames: "+datasetLoader.getFrames());

        System.out.println("Seleziona il modello:");
        Path[] modelPaths = modelLoader.getModelPathSet().toArray(new Path[0]);

        if(modelPaths.length <= 0){
            System.out.println("Nessun modello presente");
            System.exit(1);
        }

        Path modelPath = requestInput(modelPaths, inReader);
//        Path modelPath = modelPaths[0];

        Model model = modelLoader.parseModel(modelPath);
        ScreenshotRenderer.ColorType colorType = ScreenshotRenderer.ColorType.parseByteSize(model.getInputDepth());

        System.out.println(modelPath);
        System.out.println("Hai scelto: "+model.getName());
        System.out.println("Input Shape ("+model.getInputType()+"): "+ Arrays.toString(model.getInputShape()));
        System.out.println("Output Shape ("+model.getOutputType()+"): "+ Arrays.toString(model.getOutputShape()));
        System.out.println("Tipo colore utilizzato: "+colorType);

        Path[] objPaths = objectLoader.getObjPathSet().toArray(new Path[0]);

        if(objPaths.length <= 0){
            System.out.println("Nessun oggetto presente");
            System.exit(1);
        }

        Path objPath = requestInput(objPaths, inReader);
//        Path objPath = objPaths[0];
        System.out.println(objPath);
        Obj obj = objectLoader.parseObj(objPath);

        int numVertices = obj.getNumVertices();
        System.out.println("Hai scelto un oggetto con "+ numVertices + " vertici");

        Path[] texturePaths = objectLoader.getTexturePathSet().toArray(new Path[0]);

        if(texturePaths.length <= 0){
            System.out.println("Nessuna texture per oggetti presente");
            System.exit(1);
        }

        Path texturePath = requestInput(texturePaths, inReader);
//        Path texturePath = texturePaths[0];
        System.out.println(texturePath);

        BufferedImage texture = objectLoader.getTexutre(texturePath);

        System.out.println("Configurazione completata.");
        System.out.println("Procedo alla post-produzione delle immagini (cartella oma)");
        System.out.println("Salvo anche una versione depth (cartella depth)");

        //Passo all'app datasetLoader, modello, oggetto e texture

        computeScene = new ComputeScene(datasetLoader, depthPath, omaPath, model, obj, texture, colorType);

        System.out.println("Precaricamento...");
        computeScene.load();

        System.out.println("Esecuzione...");
        computeScene.run();
    }

}
