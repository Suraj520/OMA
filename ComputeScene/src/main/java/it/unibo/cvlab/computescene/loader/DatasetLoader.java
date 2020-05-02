package it.unibo.cvlab.computescene.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unibo.cvlab.computescene.dataset.PointCloudDataset;
import it.unibo.cvlab.computescene.dataset.SceneDataset;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class DatasetLoader {

    private final static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        gson = builder.create();
    }

    private Path datasetPath;
    private Path imagesPath;
    private Path scenesPath;
    private Path pointsPath;

    private int frameCounter = 0;
    private int frames = 0;

    private BufferedImage image = null;
    private SceneDataset sceneDataset = null;
    private PointCloudDataset pointDataset = null;

    public DatasetLoader(String datasetPath) throws IOException {
        this(Paths.get(datasetPath));
    }

    public DatasetLoader(Path datasetPath) throws IOException {
        this.datasetPath = datasetPath;
        this.imagesPath = this.datasetPath.resolve("images");
        this.scenesPath = this.datasetPath.resolve("scenes");
        this.pointsPath = this.datasetPath.resolve("points");

        //Conta dei frame
        //https://stackoverflow.com/questions/1844688/how-to-read-all-files-in-a-folder-from-java
        try (Stream<Path> paths = Files.list(imagesPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach((path)->frames++);
        }
    }

    public void printPaths(){
        System.out.println("Images path: "+imagesPath);
        System.out.println("Scenes path: "+scenesPath);
        System.out.println("Points path: "+pointsPath);
    }

    public void next(){
        if(hasNext()){
            frameCounter++;
            image = null;
            sceneDataset = null;
            pointDataset = null;
        }
    }

    public boolean hasNext(){
        return frameCounter + 1 < frames;
    }

    public void rewind(){
        frameCounter = 0;
        image = null;
        sceneDataset = null;
        pointDataset = null;
    }

    public Path getDatasetPath() {
        return datasetPath;
    }

    public Path getImagesPath() {
        return imagesPath;
    }

    public Path getScenesPath() {
        return scenesPath;
    }

    public Path getPointsPath() {
        return pointsPath;
    }

    public int currentFrame(){
        return frameCounter;
    }

    public int getFrames() {
        return frames;
    }

    public BufferedImage getImage() throws IOException {
        if(image != null)
            return image;

        Path imagePath = imagesPath.resolve(frameCounter+".jpg");
        image = ImageIO.read(imagePath.toFile());

        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = newImage.createGraphics();
        graphics.drawImage(image, 0,0,image.getWidth(), image.getHeight(), null);
        graphics.dispose();
        image = newImage;

        return image;
    }

    public SceneDataset parseSceneDataset() throws IOException {
        if(sceneDataset != null)
            return sceneDataset;

        Path scenePath = scenesPath.resolve(frameCounter+".json");
        Reader reader = Files.newBufferedReader(scenePath);
        sceneDataset = gson.fromJson(reader, SceneDataset.class);
        reader.close();
        return sceneDataset;
    }

    public PointCloudDataset parsePointDataset() throws IOException{
        if(pointDataset != null)
            return pointDataset;

        Path pointPath = pointsPath.resolve(frameCounter+".json");
        Reader reader = Files.newBufferedReader(pointPath);
        pointDataset = gson.fromJson(reader, PointCloudDataset.class);
        reader.close();
        return pointDataset;
    }

}
