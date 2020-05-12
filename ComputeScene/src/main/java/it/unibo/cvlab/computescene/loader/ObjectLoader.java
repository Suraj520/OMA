package it.unibo.cvlab.computescene.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import de.matthiasmann.twl.utils.PNGDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

public class ObjectLoader {
    private static Path defaultObjsPath = Paths.get("objs").toAbsolutePath().resolve("objs.json");
    private final static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        gson = builder.create();
    }

    public static class Object {
        @Expose
        @SerializedName("name")
        private String name;

        @Expose
        @SerializedName("obj")
        private String objName;

        @Expose
        @SerializedName("texture")
        private String textureName;

        @Expose
        @SerializedName("scaleFactor")
        private float scaleFactor;

        @Expose
        @SerializedName("delta")
        private float delta;

        private Obj obj;
        private PNGDecoder texture;

        public Object(String name, String objName, String textureName, float scaleFactor, float delta) {
            this.name = name;
            this.objName = objName;
            this.textureName = textureName;
            this.scaleFactor = scaleFactor;
            this.delta = delta;
        }

        public String getName() {
            return name;
        }

        public String getObjName() {
            return objName;
        }

        public String getTextureName() {
            return textureName;
        }

        public float getScaleFactor() {
            return scaleFactor;
        }

        public float getDelta() {
            return delta;
        }

        public Obj getObj() {
            return obj;
        }

        public PNGDecoder getTexture() {
            return texture;
        }

        private void setObj(Obj obj) {
            this.obj = obj;
        }

        private void setTexture(PNGDecoder texture) {
            this.texture = texture;
        }
    }

    private Path objsPath;
    private Path objsDirPath;

    public ObjectLoader() {
        this(defaultObjsPath);
    }

    public ObjectLoader(Path objsPath) {
        this.objsPath = objsPath;
        this.objsDirPath = objsPath.getParent();
    }

    public Path getObjsPath() {
        return objsPath;
    }

    public Path getObjsDirPath() {
        return objsDirPath;
    }

    public Object[] parseObjectList() throws IOException {
        if(!Files.exists(objsPath)){
            throw new IllegalStateException("Path non corretto: "+ objsPath);
        }

        Object[] objects = gson.fromJson(Files.newBufferedReader(objsPath), Object[].class);

        for (Object object: objects) {
            object.setObj(getObj(object));
            object.setTexture(getTexture(object));
        }
        return objects;
    }

    private Obj getObj(Object object) throws IOException {
        Path objPath = objsDirPath.resolve(object.getObjName());
        if(Files.exists(objPath)){
            Obj obj = ObjReader.read(Files.newInputStream(objPath));
            obj = ObjUtils.convertToRenderable(obj);
            return obj;
        }
        throw new IllegalArgumentException("Obj path non presente nel set");
    }

    private PNGDecoder getTexture(Object object) throws IOException {
        Path texturePath = objsDirPath.resolve(object.getTextureName());
        if(Files.exists(texturePath)){
            PNGDecoder decoder = new PNGDecoder(Files.newInputStream(texturePath));
            return decoder;
        }
        throw new IllegalArgumentException("Texture path non presente nel set");
    }

}
