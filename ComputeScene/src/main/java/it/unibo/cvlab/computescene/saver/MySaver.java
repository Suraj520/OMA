package it.unibo.cvlab.computescene.saver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MySaver {

    private Path datasetPath;
    private Path resultsPath;
    private Path depthPath;
    private Path scaledDepthPath;
    private Path omaPath;

    public MySaver(Path datasetPath, String modelName) {
        this.datasetPath = datasetPath;
        this.resultsPath = this.datasetPath.resolve("results");
        this.depthPath = this.resultsPath.resolve(modelName).resolve("depth");
        this.scaledDepthPath = this.resultsPath.resolve(modelName).resolve("scaledDepth");
        this.omaPath = this.resultsPath.resolve(modelName).resolve("oma");
    }

    public void mkdir() throws IOException {
        if(!Files.isDirectory(resultsPath))
            Files.createDirectories(resultsPath);

        if(!Files.isDirectory(depthPath))
            Files.createDirectories(depthPath);

        if(!Files.isDirectory(scaledDepthPath))
            Files.createDirectories(scaledDepthPath);

        if(!Files.isDirectory(omaPath))
            Files.createDirectories(omaPath);
    }

    public void printPaths(){
        System.out.println("Results path: "+resultsPath);
        System.out.println("Depth path: " + depthPath);
        System.out.println("ScaledDepth path: "+depthPath);
        System.out.println("OMA path: "+omaPath);
    }

    public Path getDatasetPath() {
        return datasetPath;
    }

    public Path getResultsPath() {
        return resultsPath;
    }

    public Path getDepthPath() {
        return depthPath;
    }

    public Path getScaledDepthPath() {
        return scaledDepthPath;
    }

    public Path getOmaPath() {
        return omaPath;
    }

    public void saveOMA(String name, ByteBuffer buffer, int width, int height, int imageType, int bpp) throws IOException {
        BufferedImage screenshot = new BufferedImage(width, height, imageType);

        for(int x = 0; x < width; x++)
        {
            for(int y = 0; y < height; y++)
            {
                int i = (x + (width * y)) * bpp;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                screenshot.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        saveOMA(name, screenshot);
    }

    public void saveOMA(long counter, ByteBuffer buffer, int width, int height, int imageType, int bpp) throws IOException {
        saveOMA(Long.toString(counter), buffer, width, height, imageType, bpp);
    }

    public void saveOMA(String name, BufferedImage image) throws IOException {
        save(name, omaPath, image);
    }

    public void saveDepth(String name, BufferedImage image) throws IOException {
        save(name, depthPath, image);
    }

    public void saveScaledDepth(String name, BufferedImage image) throws IOException {
        save(name, scaledDepthPath, image);
    }

    public void saveOMA(long counter, BufferedImage image) throws IOException {
        saveOMA(Long.toString(counter), image);
    }

    public void saveDepth(long counter, BufferedImage image) throws IOException {
        saveDepth(Long.toString(counter), image);
    }

    public void saveScaledDepth(long counter, BufferedImage image) throws IOException {
        saveScaledDepth(Long.toString(counter), image);
    }

    public static void save(String name, Path myPath, BufferedImage image) throws IOException {
        OutputStream outputStreamDepth = Files.newOutputStream(myPath.resolve(name + ".jpg"));
        ImageIO.write(image, "jpg", outputStreamDepth);
        outputStreamDepth.close();
    }
}
