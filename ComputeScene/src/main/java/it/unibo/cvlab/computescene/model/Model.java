package it.unibo.cvlab.computescene.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.tensorflow.Graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class Model {

    public enum Type{
        uint8(Byte.BYTES, false),
        float32(Float.BYTES, true),;

        private int byteSize;
        private boolean normalizationNeeded;

        Type(int byteSize, boolean normalizationNeeded) {
            this.byteSize = byteSize;
            this.normalizationNeeded = normalizationNeeded;
        }

        public int getByteSize() {
            return byteSize;
        }

        public boolean isNormalizationNeeded() {
            return normalizationNeeded;
        }
    }

    @Expose
    @SerializedName("name")
    private String name;

    @Expose
    @SerializedName("fileName")
    private String fileName;

    @Expose
    @SerializedName("inputNodes")
    private Set<String> inputNodes;
    @Expose
    @SerializedName("outputNodes")
    private Set<String> outputNodes;

    @Expose
    @SerializedName("defaultInputNode")
    private String defaultInputNode;

    @Expose
    @SerializedName("defaultOutputNode")
    private String defaultOutputNode;

    //Tipicamente [1,448,640,3]
    @Expose
    @SerializedName("inputShape")
    private int[] inputShape;
    private long[] inputShapeLong;
    //Tipicamente float32
    @Expose
    @SerializedName("inputType")
    private Type inputType;

    //Tipicamente [1,448,640,1]
    @Expose
    @SerializedName("outputShape")
    private int[] outputShape;
    private long[] outputShapeLong;
    //Tipicamente float32
    @Expose
    @SerializedName("outputType")
    private Type outputType;

    private byte[] modelBytes;
    private Path modelsPath;

    public Path getModelsPath() {
        return modelsPath;
    }

    public void setModelsPath(Path modelsPath) {
        this.modelsPath = modelsPath;
    }

    public Model(String name, String fileName, Set<String> inputNodes, Set<String> outputNodes, String defaultInputNode, String defaultOutputNode, int[] inputShape, Type inputType, int[] outputShape, Type outputType) {
        this.name = name;
        this.fileName = fileName;
        this.inputNodes = inputNodes;
        this.outputNodes = outputNodes;
        this.defaultInputNode = defaultInputNode;
        this.defaultOutputNode = defaultOutputNode;
        this.inputShape = inputShape;
        this.inputType = inputType;
        this.outputShape = outputShape;
        this.outputType = outputType;
    }

    public void addOutputNodes(String node){
        this.outputNodes.add(node);
    }

    public void addInputNode(String node){
        this.inputNodes.add(node);
    }

    public Graph getGraphDef(Path path) throws IOException {
        if(modelBytes == null){
            Path filePath = path.resolve(fileName);
            modelBytes = Files.readAllBytes(filePath);
        }

        Graph myGraph = new Graph();
        myGraph.importGraphDef(modelBytes);
        return myGraph;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDefaultInputNode() {
        return defaultInputNode;
    }

    public String getDefaultOutputNode() {
        return defaultOutputNode;
    }

    public int[] getInputShape() {
        return inputShape;
    }

    public long[] getInputShapeLong(){
        if(inputShapeLong == null){
            inputShapeLong = new long[inputShape.length];

            for (int i = 0; i < inputShape.length; i++) {
                inputShapeLong[i] = inputShape[i];
            }
        }

        return inputShapeLong;
    }

    public Type getInputType() {
        return inputType;
    }

    public int[] getOutputShape() {
        return outputShape;
    }

    public long[] getOutputShapeLong(){
        if(outputShapeLong == null){
            outputShapeLong = new long[outputShape.length];

            for (int i = 0; i < outputShape.length; i++) {
                outputShapeLong[i] = outputShape[i];
            }
        }

        return outputShapeLong;
    }

    public Type getOutputType() {
        return outputType;
    }

    public int getInputHeight()
    {
        return inputShape[1];
    }

    public int getInputWidth()
    {
        return inputShape[2];
    }

    public int getInputDepth()
    {
        return inputShape[3];
    }

    public int getOutputHeight(){
        return outputShape[1];
    }

    public int getOutputWidth(){
        return outputShape[2];
    }

    public int getOutputDepth(){
        return outputShape[3];
    }

    public int calculateInputBufferSize(boolean takeCareOfInputTypeSize){
        return inputShape[1] * inputShape[2] * inputShape[3] * (takeCareOfInputTypeSize ? inputType.getByteSize() : 1);
    }

    public int calculateOutputBufferSize(boolean takeCareOfOutputTypeSize){
        return outputShape[1] * outputShape[2] * outputShape[3] * (takeCareOfOutputTypeSize ? outputType.getByteSize() : 1);
    }

    public boolean isNormalizationNeeded(){
        return inputType.isNormalizationNeeded();
    }
}
