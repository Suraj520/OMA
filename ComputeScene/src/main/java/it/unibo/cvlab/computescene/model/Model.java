package it.unibo.cvlab.computescene.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class Model {

    public enum Type{
        @SerializedName("uint8")
        UINT_8("uint8", Byte.BYTES, false),
        @SerializedName("float32")
        FLOAT_32("float32", Float.BYTES, true),;

        private int byteSize;
        private boolean normalizationNeeded;
        private String name;

        Type(String name, int byteSize, boolean normalizationNeeded) {
            this.name = name;
            this.byteSize = byteSize;
            this.normalizationNeeded = normalizationNeeded;
        }

        public int getByteSize() {
            return byteSize;
        }

        public boolean isNormalizationNeeded() {
            return normalizationNeeded;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Normalization{
        @SerializedName("none")
        NONE,
        @SerializedName("negate")
        NEGATE,
        @SerializedName("inverse")
        INVERSE,
        @SerializedName("linear")
        LINEAR,
        @SerializedName("exp")
        EXPONENTIAL,
        @SerializedName("linearInverse")
        LINEAR_NEGATE,
        @SerializedName("expInverse")
        EXPONENTIAL_NEGATE,;
    }

    public static float[] getMinMaxOfBuffer(FloatBuffer in){
        in.rewind();

        float maxPred = Float.MIN_VALUE;
        float minPred = Float.MAX_VALUE;

        while(in.hasRemaining()){
            float pred = in.get();
            if(maxPred < pred) maxPred = pred;
            if(minPred > pred) minPred = pred;
        }

        in.rewind();

        return new float[]{minPred, maxPred};
    }

    public static FloatBuffer inverse(FloatBuffer in){
        float[] minMaxOfBuffer = getMinMaxOfBuffer(in);
        return inverse(in, minMaxOfBuffer[1]);
    }

    public static FloatBuffer inverse(FloatBuffer in,  float max){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        in.rewind();

        while(in.hasRemaining()){
            float pred = in.get();
            pred = pred / max;
            out.put(1.0f/pred);
        }

        in.rewind();
        out.rewind();

        return out;
    }

    public static FloatBuffer negate(FloatBuffer in){
        float[] minMaxOfBuffer = getMinMaxOfBuffer(in);
        return negate(in, minMaxOfBuffer[0], minMaxOfBuffer[1]);
    }

    public static FloatBuffer negate(FloatBuffer in, float min, float max){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        float range = max - min;

        in.rewind();

        while(in.hasRemaining()){
            float pred = in.get();
            out.put(range * (1f-((pred-min)/range)));
        }

        in.rewind();
        out.rewind();

        return out;
    }

    public static FloatBuffer linearNormalization(FloatBuffer in){
        float[] minMaxOfBuffer = getMinMaxOfBuffer(in);
        return linearNormalization(in, minMaxOfBuffer[0], minMaxOfBuffer[1]);
    }

    public static FloatBuffer linearNormalization(FloatBuffer in, float min, float max){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        float range = max - min;

        while(in.hasRemaining()){
            float pred = in.get();
            out.put((pred-min)/range);
        }

        in.rewind();
        out.rewind();

        return out;
    }

    public static FloatBuffer negateLinearNormalization(FloatBuffer in){
        float[] minMaxOfBuffer = getMinMaxOfBuffer(in);
        return negateLinearNormalization(in, minMaxOfBuffer[0], minMaxOfBuffer[1]);
    }

    public static FloatBuffer negateLinearNormalization(FloatBuffer in, float min, float max){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        float range = max - min;

        in.rewind();

        while(in.hasRemaining()){
            float pred = in.get();
            out.put(1f-((pred-min)/range));
        }

        in.rewind();
        out.rewind();

        return out;
    }

    public static FloatBuffer expNormalization(FloatBuffer in){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        in.rewind();

        float maxPred = Float.MIN_VALUE;
        float minPred = Float.MAX_VALUE;

        while(in.hasRemaining()){
            float pred = in.get();
            pred = (float) Math.exp(pred);
            if(maxPred < pred) maxPred = pred;
            if(minPred > pred) minPred = pred;
        }

        float range = maxPred - minPred;

        in.rewind();

        while(in.hasRemaining()){
            float pred = in.get();
            pred = (float) Math.exp(pred);
            out.put((pred-minPred)/range);
        }

        in.rewind();
        out.rewind();

        return out;
    }

    public static FloatBuffer negateExpNormalization(FloatBuffer in){
        FloatBuffer out = FloatBuffer.allocate(in.capacity());

        in.rewind();

        float maxPred = Float.MIN_VALUE;
        float minPred = Float.MAX_VALUE;

        while(in.hasRemaining()){
            float pred = in.get();
            pred = (float) Math.exp(-pred);
            if(maxPred < pred) maxPred = pred;
            if(minPred > pred) minPred = pred;
        }

        float range = maxPred - minPred;

        in.rewind();

        while(in.hasRemaining()){
            float pred = in.get();
            pred = (float) Math.exp(-pred);
            out.put((pred-minPred)/range);
        }

        in.rewind();
        out.rewind();

        return out;
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

    @Expose
    @SerializedName("inputMin")
    private float inputMin;
    @Expose
    @SerializedName("inputMax")
    private float inputMax;

    //Tipicamente [1,448,640,1]
    @Expose
    @SerializedName("outputShape")
    private int[] outputShape;
    private long[] outputShapeLong;
    //Tipicamente float32
    @Expose
    @SerializedName("outputType")
    private Type outputType;

    @Expose
    @SerializedName("outputNormalization")
    private Normalization outputNormalization;

    @Expose
    @SerializedName("outputMin")
    private float outputMin;
    @Expose
    @SerializedName("outputMax")
    private float outputMax;

    @Expose
    @SerializedName("plasmaFactor")
    private float plasmaFactor;

    private byte[] modelBytes;
    private Path modelsPath;

    public Path getModelsPath() {
        return modelsPath;
    }

    public void setModelsPath(Path modelsPath) {
        this.modelsPath = modelsPath;
    }

    private Session session;



    public Model(String name, String fileName, Set<String> inputNodes, Set<String> outputNodes, String defaultInputNode,
                 String defaultOutputNode, int[] inputShape, Type inputType, float inputMin, float inputMax,
                 int[] outputShape, Type outputType, Normalization outputNormalization, float outputMin, float outputMax) {
        this.name = name;
        this.fileName = fileName;
        this.inputNodes = inputNodes;
        this.outputNodes = outputNodes;
        this.defaultInputNode = defaultInputNode;
        this.defaultOutputNode = defaultOutputNode;
        this.inputShape = inputShape;
        this.inputType = inputType;
        this.inputMin = inputMin;
        this.inputMax = inputMax;
        this.outputShape = outputShape;
        this.outputType = outputType;
        this.outputNormalization = outputNormalization;
        this.outputMin = outputMin;
        this.outputMax = outputMax;
    }

    public void loadGraph() throws IOException {
        Graph graphDef = getGraphDef(getModelsPath());
        session = new Session(graphDef);
    }

    public List<Tensor<?>> run(Tensor<?> inputTensor){
        Session.Runner runner = session.runner();
        runner = runner.feed(getDefaultInputNode(), inputTensor);
        runner = runner.fetch(getDefaultOutputNode());
        return runner.run();
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

    public Normalization getOutputNormalization() {
        return outputNormalization;
    }

    public float getPlasmaFactor() {
        return plasmaFactor;
    }

    public float getInputMin() {
        return inputMin;
    }

    public float getInputMax() {
        return inputMax;
    }

    public float getOutputMin() {
        return outputMin;
    }

    public float getOutputMax() {
        return outputMax;
    }

    public boolean isInputMinMaxValid(){
        return inputMin < inputMax;
    }

    public boolean isOutputMinMaxValid(){
        return outputMin < outputMax;
    }

    public FloatBuffer normalize(FloatBuffer in){
        Normalization type = getOutputNormalization();

        if(type == null) type = Normalization.NONE;

        switch (type){
            case NONE:
                return in;
            case INVERSE:
                if(isOutputMinMaxValid())
                    return inverse(in, getOutputMax());
                return inverse(in);
            case NEGATE:
                if(isOutputMinMaxValid())
                    return negate(in, getOutputMin(), getOutputMax());
                return negate(in);
            case LINEAR:
                if(isOutputMinMaxValid())
                    return linearNormalization(in, getOutputMin(), getOutputMax());
                return linearNormalization(in);
            case LINEAR_NEGATE:
                if(isOutputMinMaxValid())
                    return negateLinearNormalization(in, getOutputMin(), getOutputMax());
                return negateLinearNormalization(in);
            case EXPONENTIAL:
                return  expNormalization(in);
            case EXPONENTIAL_NEGATE:
                return negateExpNormalization(in);
            default:
                throw new IllegalArgumentException("Normalizzazione non riconosciuta");
        }
    }

}
