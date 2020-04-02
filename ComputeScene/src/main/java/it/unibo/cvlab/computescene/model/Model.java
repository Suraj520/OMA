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
        uint8(1, false),
        float32(4, true),;

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
    //Tipicamente float32
    @Expose
    @SerializedName("inputType")
    private Type inputType;

    //Tipicamente [1,448,640,1]
    @Expose
    @SerializedName("outputShape")
    private int[] outputShape;
    //Tipicamente float32
    @Expose
    @SerializedName("outputType")
    private Type outputType;

    private byte[] modelBytes;
    private Path modelsPath;

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

    public Type getInputType() {
        return inputType;
    }

    public int[] getOutputShape() {
        return outputShape;
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

    public int calculateInputBufferSize(){
        return inputShape[1] * inputShape[2] * inputShape[3] *  inputType.getByteSize();
    }

    public int calculateOutputBufferSize(){
        return outputShape[1] * outputShape[2] * outputShape[3] *  outputType.getByteSize();
    }

    public boolean isNormalizationNeeded(){
        return inputType.isNormalizationNeeded();
    }
}
