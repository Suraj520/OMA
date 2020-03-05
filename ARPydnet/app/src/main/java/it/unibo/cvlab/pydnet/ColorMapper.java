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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.List;

import it.unibo.cvlab.Runner;

public class ColorMapper {

    private static final String TAG = ColorMapper.class.getSimpleName();

    static{
        System.loadLibrary("native-color");
    }

    private final float scaleFactor;
    private final int[] colorMap;
    private int[] output;
    private boolean isPrepared = false;
    private Runner[] pool;
    private Utils.Resolution resolution;

    private boolean useNative = true;

    public ColorMapper(float scaleFactor, int poolSize){
        this.scaleFactor = scaleFactor;

        int i = 0;

        List<String> plasma = Utils.getPlasma();
        this.colorMap = new int[plasma.size()];

        for(String color : plasma){
            colorMap[i++] = Color.parseColor(color);
        }

        pool = new Runner[poolSize];

        for (i = 0; i < poolSize; i++) {
            pool[i] = new Runner();
            pool[i].start();
        }
    }

    public void dispose() throws InterruptedException{
        for (Runner runner : pool) {
            runner.stop();
        }
    }

    public void prepare(Utils.Resolution resolution){
        this.resolution = resolution;
        this.output = new int[resolution.getHeight()*resolution.getWidth()];
        isPrepared = true;
    }

    private native int applyColorMap(int start, int end, FloatBuffer inference, float scaleFactor, int[] colorMap, int[] output);

    public Bitmap getColorMap(final FloatBuffer inference, int numberThread) {
        if (!isPrepared) {
            throw new RuntimeException("ColorMapper is not prepared.");
        }

        if(pool.length < numberThread){
            numberThread = pool.length;
        }

        inference.rewind();

        int inferenceLength = inference.remaining();
        int length = Math.round(inferenceLength / (float)numberThread);

        for (int index = 0; index < numberThread; index++) {
            int current_start = index*length;
            int current_end = current_start + length;
            current_end = Math.min(current_end, inferenceLength);

            final int curStart = current_start;
            final int curEnd = current_end;

            pool[index].doJob(()->{
                if(useNative){
                    try{
                        applyColorMap(curStart, curEnd, inference, scaleFactor, colorMap, output);
                    }catch (UnsatisfiedLinkError ex){
                        useNative = false;
                        Log.w(TAG, "Impossibile usare il nativo del color");
                    }
                }else{
                    for(int i = curStart; i < curEnd; i++){
                        float prediction = inference.get(i);
                        int colorIndex =  (int)(prediction * scaleFactor);
                        colorIndex = Math.min(Math.max(colorIndex, 0), colorMap.length - 1);
                        output[i] = colorMap[colorIndex];
                    }
                }
            });
        }

        try {
            for (Runner thread : pool)
                thread.waitJob();

            Bitmap bmp = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.setPixels(output, 0, resolution.getWidth(), 0, 0, resolution.getWidth(), resolution.getHeight());

            return bmp;

        }
        catch (InterruptedException e){
            Log.d(TAG, "Thread Interrotto nel join", e);
            return null;
        }

    }

}
