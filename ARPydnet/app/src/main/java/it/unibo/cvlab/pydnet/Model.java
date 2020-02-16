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
import android.graphics.Bitmap;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import it.unibo.cvlab.pydnet.Utils.*;

public abstract class Model{

    protected final String checkpoint;
    protected Map<Scale, String> outputNodes;
    protected Set<Resolution> validResolutions;
    protected ModelFactory.GeneralModel generalModel;
    protected String name;
    protected HashMap<String, float[]> results;
    protected HashMap<String, String> inputNodes;

    protected ByteBuffer input;
    protected ByteBuffer inference;
    protected ByteBuffer normalizedInference;

    protected boolean prepared;

    public boolean isPrepared(){
        return prepared;
    }

    public Model(Context context, ModelFactory.GeneralModel generalModel, String name, String checkpoint){
        this.prepared = false;
        this.generalModel = generalModel;
        this.name = name;
        this.outputNodes = new HashMap<>();
        this.inputNodes = new HashMap<>();
        this.checkpoint = checkpoint;
        this.validResolutions = new TreeSet<>();
        this.results = new HashMap<>();
    }


    public void addOutputNodes(Scale scale, String node){
            this.outputNodes.put(scale,node);
    }

    public void addInputNode(String name, String node){
        if(!this.inputNodes.containsKey(name))
            this.inputNodes.put(name, node);
    }

    public void addValidResolution(Resolution resolution){
        this.validResolutions.add(resolution);
    }

    public String getInputNode(String name){
        return this.inputNodes.get(name);
    }

    public abstract void prepare(Utils.Resolution resolution);

    public abstract void loadInput(Bitmap input);

    public abstract FloatBuffer doInference(Utils.Scale scale);


}


