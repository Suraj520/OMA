#include <jni.h>

//
// Created by luca on 05/03/20.
//

JNIEXPORT jint

JNICALL
Java_it_unibo_cvlab_pydnet_ColorMapper_applyColorMap(JNIEnv *env, jobject thiz, jint start,
                                                     jint end, jobject inference,
                                                     jfloat scale_factor, jintArray color_map,
                                                     jintArray output) {

    jfloat *inferencePointer = (jfloat*)(*env)->GetDirectBufferAddress(env, inference);
    jint *colorMapPointer = (*env)->GetIntArrayElements(env, color_map, 0);
    jint *outputPointer = (*env)->GetIntArrayElements(env, output, 0);

    int i = start;

    jsize lenColorMap = (*env)->GetArrayLength(env, color_map);
    lenColorMap = lenColorMap-1;

    while(i < end){
        jfloat prediction = inferencePointer[i];

        int colorIndex = (int)(prediction * scale_factor);
        colorIndex = colorIndex > 0 ? colorIndex : 0;
        colorIndex = colorIndex > lenColorMap ? lenColorMap : colorIndex;
        outputPointer[i] = colorMapPointer[colorIndex];

        i++;
    }

    return 0;

}
