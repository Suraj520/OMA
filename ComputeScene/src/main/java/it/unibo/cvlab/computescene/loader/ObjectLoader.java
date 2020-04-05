package it.unibo.cvlab.computescene.loader;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ObjectLoader {
    private static Path defaultObjsPath = Paths.get("objs").toAbsolutePath();

    public static String getSimpleObjName(Path objPath){
        Path fileName = objPath.getFileName();
        String fileString = fileName.toString().trim();
        return fileString.replace(".obj", "");
    }

    private Path objsPath;
    private Set<Path> objPathSet = new HashSet<>();
    private Set<Path> texturePathSet = new HashSet<>();

    public ObjectLoader() throws IOException {
        this(defaultObjsPath);
    }

    public ObjectLoader(Path objsPath) throws IOException {
        this.objsPath = objsPath;

        try (Stream<Path> paths = Files.list(objsPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter((path)->path.getFileName().toString().endsWith(".obj"))
                    .forEach(objPathSet::add);
        }

        try (Stream<Path> paths = Files.list(objsPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter((path)->path.getFileName().toString().endsWith(".png") || path.getFileName().toString().endsWith(".jpg"))
                    .forEach(texturePathSet::add);
        }
    }

    public Path getObjsPath() {
        return objsPath;
    }

    public Set<Path> getObjPathSet() {
        return objPathSet;
    }

    public Set<Path> getTexturePathSet() {
        return texturePathSet;
    }

    public Obj parseObj(Path objPath) throws IOException{
        if(objPathSet.contains(objPath)){
            Obj obj = ObjReader.read(Files.newInputStream(objPath));
            obj = ObjUtils.convertToRenderable(obj);
            return obj;
        }
        throw new IllegalArgumentException("Obj path non presente nel set");
    }

    public BufferedImage getTexutre(Path texturePath) throws IOException{
        if(texturePathSet.contains(texturePath)){
            return ImageIO.read(texturePath.toFile());
        }
        throw new IllegalArgumentException("Texture path non presente nel set");
    }

}
