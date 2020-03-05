#include <jni.h>
#include <stdint.h>

/**
 * Trasforma un buffer RGB8 in un FloatBuffer con nornalizzazione del pixel [0,1].
 * Non vengono effettuati controlli sui parametri.
 *
 * @param env
 * @param clazz
 * @param in buffer in ingresso RGB8
 * @param out float buffer in uscita.
 * @param in_length lunghezza buffer in ingresso
 * @param out_length lunghezza buffer uscita
 * @return 0
 */
JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_RGBbufferNormalization(JNIEnv* env,
                                                                        jclass clazz,
                                                                        jobject in,
                                                                        jobject out,
                                                                        jint in_length,
                                                                        jint out_length) {
    uint8_t *inPointer = (uint8_t*)(*env)->GetDirectBufferAddress(env, in);
    float *outPointer = (float*)(*env)->GetDirectBufferAddress(env, out);

    long i = 0;
    long j = 0;

    while(i < in_length && j < out_length){
        outPointer[j] = (inPointer[i+2] / 255.0f);
        outPointer[j+1] = (inPointer[i+1] / 255.0f);
        outPointer[j+2] = (inPointer[i] / 255.0f);
        i += 3;
        j += 3;
    }

    return 0;
}


/**
 * Trasforma un buffer ARGB8 in un FloatBuffer con nornalizzazione del pixel [0,1].
 * Non vengono effettuati controlli sui parametri.
 * Ancora da testare.
 *
 * @param env
 * @param clazz
 * @param in buffer in ingresso ARGB8
 * @param out float buffer in uscita.
 * @param in_length lunghezza buffer in ingresso
 * @param out_length lunghezza buffer uscita
 * @return 0
 */
JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_ARGBbufferNormalization(JNIEnv * env,
                                                                        jclass clazz,
                                                                        jobject in,
                                                                        jobject out,
                                                                        jint in_length,
                                                                        jint out_length) {

    uint8_t *inPointer = (uint8_t*)(*env)->GetDirectBufferAddress(env, in);
    float *outPointer = (float*)(*env)->GetDirectBufferAddress(env, out);

    long i = 0;
    long j = 0;

    while(i < in_length && j < out_length){
        outPointer[j] = (inPointer[i+2] / 255.0f);
        outPointer[j+1] = (inPointer[i+1] / 255.0f);
        outPointer[j+2] = (inPointer[i] / 255.0f);
        i += 4;
        j += 3;
    }

    return 0;
}

/**
 * Trasforma un buffer RGBA8 in un FloatBuffer con nornalizzazione del pixel [0,1].
 * Non vengono effettuati controlli sui parametri.
 * Ancora da testare.
 *
 * @param env
 * @param clazz
 * @param in buffer in ingresso RGBA8
 * @param out float buffer in uscita.
 * @param in_length lunghezza buffer in ingresso
 * @param out_length lunghezza buffer uscita
 * @return 0
 */
JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_RGBAbufferNormalization(JNIEnv* env,
                                                                        jclass clazz,
                                                                        jobject in,
                                                                        jobject out,
                                                                        jint in_length,
                                                                        jint out_length) {
    uint8_t *inPointer = (uint8_t*)(*env)->GetDirectBufferAddress(env, in);
    float *outPointer = (float*)(*env)->GetDirectBufferAddress(env, out);

    int i = 0;
    int j = 0;

    while(i < in_length){
        outPointer[j] = (inPointer[i] / 255.0f);
        outPointer[j+1] = (inPointer[i+1] / 255.0f);
        outPointer[j+2] = (inPointer[i+2] / 255.0f);
        i += 4;
        j += 3;
    }

    return 0;
}