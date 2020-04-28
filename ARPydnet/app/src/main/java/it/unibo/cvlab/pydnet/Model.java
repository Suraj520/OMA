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

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import it.unibo.cvlab.Runner;
import it.unibo.cvlab.pydnet.Utils.*;

public abstract class Model{

    public enum Origin{
        TOP_LEFT(0,0),
        BOTTOM_LEFT(0,1),
        TOP_RIGHT(1,0),
        BOTTOM_RIGHT(1,1);

        private int x,y;

        Origin(int x, int y){
            this.x = x;
            this.y = y;
        }

        public int convertTo(int x, int y, int width, int height, Origin dst){
            //Conversione BL(0,1) <-> TL(0,0): inverto y
            //Conversione BR(1,1) <-> TR(1,0): inverto y

            //Conversione BL(0,1) <-> BR(1,1): inverto x
            //Conversione TL(0,0) <-> TR(1,0): inverto x

            //Conversione BL(0,1) <-> TR(1,0): inverto xy
            //Conversione TL(0,0) <-> BR(1,1): inverto xy

            //Sfrutto la differenza per trovare chi devo scambiare.
            int dx = this.x - dst.x;
            int dy = this.y - dst.y;

            boolean invertX = dx != 0;
            boolean invertY = dy != 0;

            if(invertX && invertY){
                int invertedX = (width-1)-x;
                int invertedY = (height-1)-y;
                return (invertedY * width) + invertedX;
            }else if(invertX){
                int invertedX = (width-1)-x;
                return (y * width) + invertedX;
            }else if(invertY){
                int invertedY = (height-1)-y;
                return (invertedY * width) + x;
            }else{
                return (y * width) + x;
            }
        }
    }

    public enum ColorConfig{
        //Formato comune A:0, R:1, G:2, B:3
        RGBA_8888_BL(new int[]{0x000000FF, 0xFF000000, 0x00FF0000, 0x0000FF00}, new int[]{0, 24, 16, 8}, Origin.BOTTOM_LEFT),
        ARGB_8888_BL(new int[]{0xFF000000, 0x00FF0000, 0x0000FF00, 0x000000FF}, new int[]{24, 16, 8, 0}, Origin.BOTTOM_LEFT),
        RGBA_8888_TL(new int[]{0x000000FF, 0xFF000000, 0x00FF0000, 0x0000FF00}, new int[]{0, 24, 16, 8}, Origin.TOP_LEFT),
        ARGB_8888_TL(new int[]{0xFF000000, 0x00FF0000, 0x0000FF00, 0x000000FF}, new int[]{24, 16, 8, 0}, Origin.TOP_LEFT),;


        public static ColorConfig parseBitmapConfig(@NonNull Bitmap.Config config){
            if (config == Bitmap.Config.ARGB_8888) {
                return ColorConfig.ARGB_8888_TL;
            }
            throw new IllegalArgumentException("Formato Bitmap non supportato");
        }

        private int[] mask;
        private int[] shift;
        private Origin origin;

        ColorConfig(int[] mask, int[] shift, Origin origin){
            this.mask = mask;
            this.shift = shift;
            this.origin = origin;
        }

        public int getAlphaMask(){
            return mask[0];
        }

        public int getAlphaShift(){
            return shift[0];
        }

        public int getRedMask(){
            return mask[1];
        }

        public int getRedShift(){
            return shift[1];
        }

        public int getGreenMask(){
            return mask[2];
        }

        public int getGreenShift(){
            return shift[2];
        }

        public int getBlueMask(){
            return mask[3];
        }

        public int getBlueShift(){
            return shift[3];
        }

        public Origin getOrigin(){
            return origin;
        }
    }

    private final String checkpoint;
    private Map<Scale, String> outputNodes;
    private ModelFactory.GeneralModel generalModel;
    protected String name;
    private HashMap<String, String> inputNodes;

    private float colorFactor;

    protected boolean prepared;

    public boolean isPrepared(){
        return prepared;
    }

    protected Resolution resolution;

    protected Runner[] pool;

    public void preparePool(int poolSize){
        pool = new Runner[poolSize];

        for (int i = 0; i < poolSize; i++) {
            pool[i] = new Runner();
            pool[i].start();
        }
    }

    Model(Context context, ModelFactory.GeneralModel generalModel, String name, String checkpoint, float colorFactor, Resolution resolution){
        this.colorFactor = colorFactor;
        this.resolution = resolution;
        this.prepared = false;
        this.generalModel = generalModel;
        this.name = name;
        this.outputNodes = new HashMap<>();
        this.inputNodes = new HashMap<>();
        this.checkpoint = checkpoint;
    }


    public void addOutputNodes(Scale scale, String node){
            this.outputNodes.put(scale,node);
    }

    public void addInputNode(String name, String node){
        if(!this.inputNodes.containsKey(name))
            this.inputNodes.put(name, node);
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public ModelFactory.GeneralModel getGeneralModel() {
        return generalModel;
    }

    public String getName() {
        return name;
    }

    public float getColorFactor() {
        return colorFactor;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public String getInputNode(String name){
        return this.inputNodes.get(name);
    }

    public abstract void prepare(Utils.Resolution resolution);

    public abstract void loadInput(Bitmap input);
    public abstract void loadInput(int[] data);
    public abstract void loadInput(ByteBuffer data);
    public abstract void loadInput(IntBuffer data);

    public abstract FloatBuffer doInference(Utils.Scale scale);
    public abstract ByteBuffer doRawInference(Utils.Scale scale);

    public void dispose(){
        if(pool != null && pool.length > 0){
            try {
                for (Runner runner : pool) {
                    runner.stop();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


