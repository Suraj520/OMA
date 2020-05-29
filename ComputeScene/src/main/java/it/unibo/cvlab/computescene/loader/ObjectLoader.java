package it.unibo.cvlab.computescene.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import de.matthiasmann.twl.utils.PNGDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ObjectLoader {
    private static Path defaultObjsPath = Paths.get("objs").toAbsolutePath().resolve("objs.json");
    private final static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        gson = builder.create();
    }

    public enum AnimationMode{
        @SerializedName("none")
        NONE,
        @SerializedName("repeat")
        REPEAT,
        @SerializedName("stopAtEnd")
        STOP_AT_END,
        @SerializedName("pingPong")
        PING_PONG,;
    }

    public static class Object {
        @Expose
        @SerializedName("name")
        private String name;

        @Expose
        @SerializedName("objs")
        private String[] objsName;

        @Expose
        @SerializedName("texture")
        private String textureName;

        @Expose
        @SerializedName("scaleFactor")
        private float scaleFactor;

        @Expose
        @SerializedName("delta")
        private float delta;

        @Expose
        @SerializedName("animation")
        private boolean animation;

        @Expose
        @SerializedName("animationMode")
        private AnimationMode animationMode;

        private ObjectLoader objLoader;
        private Obj[] objs;
        private PNGDecoder textureDecoder;

        private ByteBuffer textureBuffer;
        private int textureWidth, textureHeight;

        private int objCounter = 0;
        private boolean pingPong = true;

        public Object(String name, String[] objName, String textureName, float scaleFactor, float delta) {
            this.name = name;
            this.objsName = objsName;
            this.textureName = textureName;
            this.scaleFactor = scaleFactor;
            this.delta = delta;
        }

        public String getName() {
            return name;
        }

        public String[] getObjsName() {
            return objsName;
        }

        public int getNumberOfObjs(){
            return objsName.length;
        }

        public AnimationMode getAnimationMode() {
            return animationMode;
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

        public boolean isAnimation() {
            return animation;
        }

        public Obj getNextObj() throws IOException {
            if(objs == null){
                objs = new Obj[objsName.length];
            }

            if(objs[objCounter] == null){
                objs[objCounter] = objLoader.getObj(this);
            }

            Obj result = objs[objCounter];
            incrementCounter();
            return result;
        }

        private void decodeTexture() throws IOException {
            textureDecoder = objLoader.getTextureDecoder(this);

            //https://stackoverflow.com/questions/41901468/load-a-texture-within-opengl-and-lwjgl3-in-java/41902221
            //create a byte buffer big enough to store RGBA values
            textureBuffer = ByteBuffer.allocateDirect(4 * textureDecoder.getWidth() * textureDecoder.getHeight());
            //decode
            textureDecoder.decode(textureBuffer, textureDecoder.getWidth() * 4, PNGDecoder.Format.RGBA);
            //flip the buffer so its ready to read
            textureBuffer.flip();

            textureWidth = textureDecoder.getWidth();
            textureHeight = textureDecoder.getHeight();
        }

        public ByteBuffer getTexture() throws IOException {
            if(textureDecoder == null){
                decodeTexture();
            }

            return textureBuffer;
        }

        public int getTextureHeight() throws IOException {
            if(textureDecoder == null){
                decodeTexture();
            }

            return textureHeight;
        }

        public int getTextureWidth() throws IOException {
            if(textureDecoder == null){
                decodeTexture();
            }

            return textureWidth;
        }

        public int getObjCounter() {
            return objCounter;
        }

        private void incrementCounter(){
            //Solo per animaizoni
            if(!animation) return;

            //Switch sul modo di animazione
            switch (animationMode){
                case REPEAT://0,1,2,3,0,1,2,3,..
                    if(objCounter + 1 < objsName.length){
                        objCounter++;
                    }else{
                        objCounter = 0;
                    }
                    return;
                case PING_PONG://0,1,2,3,3,2,1,0,0,1,...
                    if(pingPong){
                        if(objCounter + 1 < objsName.length){
                            objCounter++;
                        }else{
                            pingPong = !pingPong;
                        }
                    }else {
                        if(objCounter - 1 >= 0){
                            objCounter--;
                        }else{
                            pingPong = !pingPong;
                        }
                    }
                    return;
                case STOP_AT_END://0,1,2,3
                    if(objCounter + 1 < objsName.length){
                        objCounter++;
                    }
                    return;
                case NONE://0
                default:
                    objCounter = 0;
                    return;
            }
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

        for(Object object : objects){
            object.objLoader = this;
        }

        return objects;
    }

    private Obj getObj(Object object) throws IOException {
        Path objPath = objsDirPath.resolve(object.objsName[object.objCounter]);

        if(Files.exists(objPath)){
            Obj obj = ObjReader.read(Files.newInputStream(objPath));
            obj = ObjUtils.convertToRenderable(obj);
            return obj;
        }
        throw new IllegalArgumentException("Obj path non presente nel set");
    }

    private PNGDecoder getTextureDecoder(Object object) throws IOException {
        Path texturePath = objsDirPath.resolve(object.getTextureName());
        if(Files.exists(texturePath)){
            PNGDecoder decoder = new PNGDecoder(Files.newInputStream(texturePath));
            return decoder;
        }
        throw new IllegalArgumentException("Texture path non presente nel set");
    }

}
