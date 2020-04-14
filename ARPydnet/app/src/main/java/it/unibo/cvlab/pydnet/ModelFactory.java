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
        PYDNET_PP,
        DSNET
    }

    public ModelFactory(Context context){
       this.context = context;
       this.models = new HashMap<>();

       //Aggiungo il modello
       models.put(GeneralModel.PYDNET_PP, createLitePydnetPP());
       models.put(GeneralModel.DSNET, createLiteDsnet());
    }

    public Model getModel(GeneralModel generalModel){
        return models.get(generalModel);
    }

    private Model createLitePydnetPP(){
        Model pydnetPP;
        pydnetPP = new TensorflowLiteModel(context, GeneralModel.PYDNET_PP, "Pydnet++", "pydnet_half_default.tflite");

        //L'ottmizzazione riduce la rete piramidale ad un solo layer di uscita:
        //non ho più bisogno di mappare le uscite e utilizzo il metodo più semplice.

        pydnetPP.addInputNode("image", "im0");
        pydnetPP.addOutputNodes(Scale.HALF, "PSD/resize_images/ResizeBilinear");
        pydnetPP.addOutputNodes(Scale.QUARTER, "PSD/resize_images_1/ResizeBilinear");
        pydnetPP.addOutputNodes(Scale.HEIGHT, "PSD/resize_images_2/ResizeBilinear");

        //Il modello caricato ha una specifica risoluzione d'ingresso.
        pydnetPP.addValidResolution(Utils.Resolution.RES4);

        return pydnetPP;
    }

    private Model createLiteDsnet(){
        Model pydnetPP;
        pydnetPP = new TensorflowLiteModel(context, GeneralModel.DSNET, "DSNET", "dsnet_half_default.tflite");

        //L'ottmizzazione riduce la rete piramidale ad un solo layer di uscita:
        //non ho più bisogno di mappare le uscite e utilizzo il metodo più semplice.

        pydnetPP.addInputNode("image", "im0");
        pydnetPP.addOutputNodes(Scale.HALF, "MonocularNetwork/half");
        pydnetPP.addOutputNodes(Scale.QUARTER, "MonocularNetwork/quarter");
        pydnetPP.addOutputNodes(Scale.HEIGHT, "MonocularNetwork/height");

        //Il modello caricato ha una specifica risoluzione d'ingresso.
        pydnetPP.addValidResolution(Utils.Resolution.RES4);

        return pydnetPP;
    }

}


