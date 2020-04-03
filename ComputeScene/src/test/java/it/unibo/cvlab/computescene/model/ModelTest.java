package it.unibo.cvlab.computescene.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class ModelTest {

    @Test
    public void mainTest(){
        HashSet<String> inputNodes = new HashSet<>();
        inputNodes.add("im0");

        HashSet<String> outputNodes = new HashSet<>();
        outputNodes.add("PSD/resize_images/ResizeBilinear");
        outputNodes.add("PSD/resize_images_1/ResizeBilinear");
        outputNodes.add("PSD/resize_images_2/ResizeBilinear");

        int[] inputShape = new int[]{1, 448, 640, 3};
        Model.Type inputType = Model.Type.float32;

        int[] outputShape = new int[]{1, 448, 640, 1};
        Model.Type outputType = Model.Type.float32;

        Model model = new Model("Pydnet++", "pydnet.pb", inputNodes, outputNodes, "im0", "PSD/resize_images/ResizeBilinear", inputShape, inputType, outputShape, outputType);

        GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        Gson gson = builder.create();

        String json = gson.toJson(model);

        assertEquals("{\"name\":\"Pydnet++\",\"fileName\":\"pydnet.pb\",\"inputNodes\":[\"im0\"],\"outputNodes\":[\"PSD/resize_images/ResizeBilinear\",\"PSD/resize_images_2/ResizeBilinear\",\"PSD/resize_images_1/ResizeBilinear\"],\"defaultInputNode\":\"im0\",\"defaultOutputNode\":\"PSD/resize_images/ResizeBilinear\",\"inputShape\":[1,448,640,3],\"inputType\":\"float32\",\"outputShape\":[1,448,640,1],\"outputType\":\"float32\"}", json);

        assertEquals(model.calculateInputBufferSize(true), 448*640*3*4);
        assertEquals(model.calculateInputBufferSize(false), 448*640*3);

        assertEquals(model.calculateOutputBufferSize(true), 448*640*4);
        assertEquals(model.calculateOutputBufferSize(false), 448*640);

        assertEquals("float32", model.getInputType().toString());
        assertEquals("float32", model.getOutputType().toString());

    }

}