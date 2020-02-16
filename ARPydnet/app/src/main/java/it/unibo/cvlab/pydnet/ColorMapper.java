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
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;

import it.unibo.cvlab.Runner;

public class ColorMapper {

    private static final String TAG = ColorMapper.class.getSimpleName();

//    private static final int[] strange = new int[]{3203, 3204, 3205, 3206, 3843, 3844, 3845, 3846, 4483, 4484, 4485, 4486, 5123, 5124, 5125, 5126, 5763, 5764, 5765, 5766, 6403, 6404, 6405, 6406, 7043, 7044, 7045, 7046};

    private final float scaleFactor;
    private final boolean applyColorMap;
    private final List<String> colorMap;
    private int[] output;
    private boolean isPrepared = false;
    private Runner[] pool;
    private Utils.Resolution resolution;

    public ColorMapper(float scaleFactor, boolean applyColorMap, int poolSize){
        this.scaleFactor = scaleFactor;
        this.applyColorMap = applyColorMap;
        this.colorMap = Utils.getPlasma();

        pool = new Runner[poolSize];

        for (int i = 0; i < poolSize; i++) {
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


    public Bitmap getColorMap(final FloatBuffer inference, int numberThread) {
        if (!isPrepared) {
            throw new RuntimeException("ColorMapper is not prepared.");
        }

        if(pool.length < numberThread){
            numberThread = pool.length;
        }

        inference.rewind();

        int inferenceLength = inference.remaining();
        int length = Math.round(inferenceLength / numberThread);

        for (int index = 0; index < numberThread; index++) {
            int current_start = index*length;
            int current_end = current_start + length;
            current_end = Math.min(current_end, inferenceLength);

            final int curStart = current_start;
            final int curEnd = current_end;

            pool[index].doJob(()->{
                for(int i = curStart; i < curEnd; i++){
                    float prediction = inference.get(i);

//                    if(Arrays.binarySearch(strange, i) > 0){
//                        //Valore strano: debug
//                        int x = i % resolution.getWidth();
//                        int y = (i-x)/resolution.getWidth();
//                        Log.d(TAG, "Strange: "+x+","+y);
//                        Log.d(TAG, "Value: "+prediction);
//                    }

                    if(applyColorMap){
                        int colorIndex =  (int)(prediction * scaleFactor);

//                        if(colorIndex < 0){
//                            int x = i % resolution.getWidth();
//                            int y = (i-x)/resolution.getWidth();
//                            Log.d(TAG, "Strange: "+x+","+y);
//                            Log.d(TAG, "ColorIndex: "+colorIndex);
//                        }


                        colorIndex = Math.min(Math.max(colorIndex, 0), colorMap.size() - 1);

                        String s = colorMap.get(colorIndex);

//                        if(s.equals("#0D0887")){
//                            int x = i % resolution.getWidth();
//                            int y = (i-x)/resolution.getWidth();
//                            Log.d(TAG, "Strange: "+x+","+y);
//                            Log.d(TAG, "Value: "+prediction);
//                        }

                        output[i] = Color.parseColor(s);
                    } else{
                        output[i] =  (int) (prediction * scaleFactor);
                    }

                }
            });
        }

        try {
            for (Runner thread : pool)
                thread.waitJob();


//            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//            bmp.setPixels(output, 0, width, 0, 0, resolution.getWidth(), resolution.getHeight());
//            bmp.setPixels(output, (resolution.getWidth() * (height-resolution.getHeight())), width, 0, resolution.getHeight(),  resolution.getWidth(), height-resolution.getHeight());

            Bitmap bmp = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.setPixels(output, 0, resolution.getWidth(), 0, 0, resolution.getWidth(), resolution.getHeight());

            return bmp;

        }
        catch (InterruptedException e){
            Log.d(TAG, "Thread Interrotto nel join", e);
            return null;
        }

    }

    public void getColorMap(Bitmap toCopy, final FloatBuffer inference, int numberThread) {
        if (!isPrepared) {
            throw new RuntimeException("ColorMapper is not prepared.");
        }

        if(toCopy.getWidth() != resolution.getWidth() || toCopy.getHeight() != resolution.getHeight()){
            throw new RuntimeException("Bitmap don't match resolution");
        }


        if(pool.length < numberThread){
            numberThread = pool.length;
        }

        inference.rewind();

        int inferenceLength = inference.remaining();
        int length = Math.round(inferenceLength / numberThread);

        for (int index = 0; index < numberThread; index++) {
            int current_start = index*length;
            int current_end = current_start + length;
            current_end = Math.min(current_end, inferenceLength);

            final int curStart = current_start;
            final int curEnd = current_end;

            pool[index].doJob(()->{
                for(int i = curStart; i < curEnd; i++){
                    float prediction = inference.get(i);

                    if(applyColorMap){
                        int colorIndex =  (int)(prediction * scaleFactor);
                        colorIndex = Math.min(Math.max(colorIndex, 0), colorMap.size() - 1);
                        output[i] = Color.parseColor(colorMap.get(colorIndex));
                    } else{
                        output[i] =  (int) (prediction * scaleFactor);
                    }

                }
            });
        }

        try {
            for (Runner thread : pool)
                thread.waitJob();

            toCopy.setPixels(output, 0, resolution.getWidth(), 0, 0, resolution.getWidth(), resolution.getHeight());
        }
        catch (InterruptedException e){
            Log.d(TAG, "Thread Interrotto nel join", e);
        }

    }
}
