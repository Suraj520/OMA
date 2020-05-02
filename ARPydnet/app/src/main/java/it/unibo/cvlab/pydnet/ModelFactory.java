/*
Copyright 2019 Filippo Aleotti

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

* Author: Filippo Aleotti
* Mail: filippo.aleotti2@unibo.it
*/

package it.unibo.cvlab.pydnet;

import android.content.Context;

import it.unibo.cvlab.pydnet.Utils.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ModelFactory {

    private final Context context;

    private HashMap<GeneralModel, Model> models;

    public enum GeneralModel{
        PYDNET_PP(10.5f, Resolution.RES4),
        PYDNET_PP_V2(1.0f, Resolution.RES5),
        DSNET(255.0f, Resolution.RES4);
//        TEST(0.0f, Resolution.RES_TEST);


        private float colorFactor;
        private Resolution resolution;


        GeneralModel(float colorFactor, Resolution resolution) {
            this.colorFactor = colorFactor;
            this.resolution = resolution;
        }

        public float getColorFactor() {
            return colorFactor;
        }

        public Resolution getResolution() {
            return resolution;
        }
    }

    public ModelFactory(Context context){
       this.context = context;
       this.models = new HashMap<>();

       //Aggiungo il modello
       models.put(GeneralModel.PYDNET_PP, createLitePydnetPP());
       models.put(GeneralModel.PYDNET_PP_V2, createLitePydnetPPV2());
       models.put(GeneralModel.DSNET, createLiteDsnet());
//       models.put(GeneralModel.TEST, createLiteTest());
    }

    public Model getModel(GeneralModel generalModel){
        if(!models.containsKey(generalModel))
            throw new RuntimeException("Modello non trovato");

        return models.get(generalModel);
    }

    private Model createLitePydnetPP(){
        Model pydnetPP;
        pydnetPP = new TensorflowLiteModel(context, GeneralModel.PYDNET_PP, "Pydnet++", "pydnet_height_quantized.tflite");

        //L'ottmizzazione riduce la rete piramidale ad un solo layer di uscita:
        //non ho più bisogno di mappare le uscite e utilizzo il metodo più semplice.

        pydnetPP.addInputNode("image", "im0");
        pydnetPP.addOutputNodes(Scale.HALF, "PSD/resize_images_2/ResizeBilinear");
        pydnetPP.addOutputNodes(Scale.QUARTER, "PSD/resize_images_2/ResizeBilinear");
        pydnetPP.addOutputNodes(Scale.HEIGHT, "PSD/resize_images_2/ResizeBilinear");

        return pydnetPP;
    }

    private Model createLitePydnetPPV2(){
        Model pydnetPP;
        pydnetPP = new TensorflowLiteModel(context, GeneralModel.PYDNET_PP_V2, "Pydnet++ v2", "pydnet_v2_half_quantized_int_input1.tflite");


        pydnetPP.addInputNode("image", "im0");
        pydnetPP.addOutputNodes(Scale.HALF, "half");
        pydnetPP.addOutputNodes(Scale.QUARTER, "half");
        pydnetPP.addOutputNodes(Scale.HEIGHT, "half");

        return pydnetPP;
    }

    private Model createLiteDsnet(){
        Model dsnetModel;
        dsnetModel = new TensorflowLiteModel(context, GeneralModel.DSNET, "DSNET", "dsnet_half_default.tflite");

        dsnetModel.addInputNode("image", "im0");
        dsnetModel.addOutputNodes(Scale.HALF, "MonocularNetwork/half");
        dsnetModel.addOutputNodes(Scale.QUARTER, "MonocularNetwork/half");
        dsnetModel.addOutputNodes(Scale.HEIGHT, "MonocularNetwork/half");

        return dsnetModel;
    }

//    private Model createLiteTest(){
//        Model testModel;
//        testModel = new TensorflowLiteModel(context, GeneralModel.TEST, "Test", "performance_test_quantized.tflite");
//
//        testModel.addInputNode("image", "content_image");
//        testModel.addInputNode("bias", "mobilenet_conv/Conv/BiasAdd");
//
//        testModel.addOutputNodes(Scale.HALF, "transformer/expand/conv3/conv/Sigmoid");
//        testModel.addOutputNodes(Scale.QUARTER, "transformer/expand/conv3/conv/Sigmoid");
//        testModel.addOutputNodes(Scale.HEIGHT, "transformer/expand/conv3/conv/Sigmoid");
//
//        return testModel;
//    }

}


