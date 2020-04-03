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

package it.unibo.cvlab.computescene;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ColorMapper {

    private static final String TAG = ColorMapper.class.getSimpleName();
    private final static Logger Log = Logger.getLogger(ColorMapper.class.getSimpleName());

    private final float scaleFactor;
    private final int[] colorMap;
    private int[] output;
    private boolean isPrepared = false;
    private Runner[] pool;

    private int width;
    private int height;

    private boolean useNative = true;

    public ColorMapper(float scaleFactor, int poolSize){
        this.scaleFactor = scaleFactor;

        int i = 0;

        List<Integer> plasma = Utils.getPlasma();
        this.colorMap = new int[plasma.size()];

        for(Integer color : plasma){
            colorMap[i++] = color;
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

    public void prepare(int width, int height){
        this.width = width;
        this.height = height;
        this.output = new int[width * height];
        isPrepared = true;
    }

    private native int applyColorMap(int start, int end, FloatBuffer inference, float scaleFactor, int[] colorMap, int[] output);

    public BufferedImage getColorMap(final FloatBuffer inference, int numberThread) {
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
                for(int i = curStart; i < curEnd; i++){
                    float prediction = inference.get(i);
                    int colorIndex =  (int)(prediction * scaleFactor);
                    colorIndex = Math.min(Math.max(colorIndex, 0), colorMap.length - 1);
                    output[i] = colorMap[colorIndex];
                }
            });
        }

        try {
            for (Runner thread : pool)
                thread.waitJob();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0,0, width, height, output, 0, width);

            return image;

        }
        catch (InterruptedException e){
            Log.log(Level.INFO, "Thread Interrotto nel join", e);
            return null;
        }

    }

}
