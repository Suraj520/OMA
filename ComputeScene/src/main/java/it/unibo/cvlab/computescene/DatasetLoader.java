package it.unibo.cvlab.computescene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unibo.cvlab.computescene.dataset.SceneDataset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class DatasetLoader {

    private static Path defaultDatasetPath = Paths.get("./dataset");
    private final static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        gson = builder.create();
    }

    private Path datasetPath;
    private Path imagesPath;
    private Path posesPath;

    private int frameCounter = 0;
    private int frames = 0;

    private BufferedImage image = null;
    private SceneDataset dataset = null;

    public DatasetLoader() throws IOException {
        this(defaultDatasetPath);
    }

    public DatasetLoader(String datasetPath) throws IOException {
        this(Paths.get(datasetPath));
    }

    public DatasetLoader(Path datasetPath) throws IOException {
        this.datasetPath = datasetPath;
        this.imagesPath = this.datasetPath.resolve("images");
        this.posesPath = this.datasetPath.resolve("scenes");

        //Conta dei frame
        //https://stackoverflow.com/questions/1844688/how-to-read-all-files-in-a-folder-from-java
        try (Stream<Path> paths = Files.walk(imagesPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach((path)->frames++);
        }
    }

    public void next(){
        if(hasNext()){
            frameCounter++;
            image = null;
            dataset = null;
        }
    }

    public boolean hasNext(){
        return frameCounter + 1 < frames;
    }

    public void rewind(){
        frameCounter = 0;
        image = null;
        dataset = null;
    }

    public Path getDatasetPath() {
        return datasetPath;
    }

    public Path getImagesPath() {
        return imagesPath;
    }

    public Path getPosesPath() {
        return posesPath;
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
        return image;
    }

    public SceneDataset parseDataset() throws IOException {
        if(dataset != null)
            return dataset;

        Path scenePath = imagesPath.resolve(frameCounter+".json");
        Reader reader = Files.newBufferedReader(scenePath);
        dataset = gson.fromJson(reader, SceneDataset.class);
        reader.close();
        return dataset;
    }

}
