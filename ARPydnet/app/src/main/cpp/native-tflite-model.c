//
// Created by luca on 06/03/20.
//


#include <tensorflow/lite/c/c_api.h>
#include <tensorflow/lite/delegates/gpu/delegate.h>
#include <jni.h>

#define NATIVE_MESSAGE_SUCCESS 0
#define NATIVE_MESSAGE_ERROR_INIT 1
#define NATIVE_MESSAGE_ERROR_READ_MODEL 2
#define NATIVE_MESSAGE_ERROR_OPTIONS 3
#define NATIVE_MESSAGE_ERROR_INTERPRETER 4
#define NATIVE_MESSAGE_ERROR_COPYBUFFER 5
#define NATIVE_MESSAGE_ERROR_TENSOR 6
#define NATIVE_MESSAGE_ERROR_INVALID_SIZE 7

static TfLiteModel *model;
static TfLiteInterpreterOptions *options;
static TfLiteGpuDelegateOptionsV2 gpuOptions;
static TfLiteInterpreter *interpreter;

static TfLiteTensor* input_tensor;
static TfLiteTensor* output_tensor;

JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_testNativeTfLiteFunctions(JNIEnv *env,
                                                                         jclass clazz) {
    return (jint)NATIVE_MESSAGE_SUCCESS;
}


JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_createInterpreter(JNIEnv *env, jobject thiz,
                                                                 jobject model,
                                                                       jlong model_size,
                                                                       jboolean use_gpu) {

    if(interpreter != NULL) return (jint) NATIVE_MESSAGE_ERROR_INIT;


    void *modelPointer = (void*)(*env)->GetDirectBufferAddress(env, model);
    model = TfLiteModelCreate(modelPointer, (size_t)model_size);

    if(model == NULL)
        return (jint)NATIVE_MESSAGE_ERROR_READ_MODEL;

    options = TfLiteInterpreterOptionsCreate();

    if(options == NULL)
        return (jint) NATIVE_MESSAGE_ERROR_OPTIONS;

    TfLiteInterpreterOptionsSetNumThreads(options, 4);

    // Create the interpreter.
    interpreter = TfLiteInterpreterCreate(model, options);

    if(interpreter == NULL)
        return (jint)NATIVE_MESSAGE_ERROR_INTERPRETER;

    if(use_gpu == JNI_TRUE){
        gpuOptions = TfLiteGpuDelegateOptionsV2Default();

        gpuOptions.inference_priority1 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_LATENCY;
        gpuOptions.inference_priority2 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
        gpuOptions.inference_priority3 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
        gpuOptions.is_precision_loss_allowed = true;

        TfLiteDelegate *gpuDelegate = TfLiteGpuDelegateV2Create(&gpuOptions);

        TfLiteInterpreterOptionsAddDelegate(options, gpuDelegate);
    }else{
        // Allocate tensors and populate the input tensor data.
        TfLiteInterpreterAllocateTensors(interpreter);
    }

    input_tensor = TfLiteInterpreterGetInputTensor(interpreter, 0);
    if(input_tensor == NULL)
        return (jint) NATIVE_MESSAGE_ERROR_TENSOR;

    output_tensor = (TfLiteTensor*)TfLiteInterpreterGetOutputTensor(interpreter, 0);

    if(output_tensor == NULL)
        return (jint) NATIVE_MESSAGE_ERROR_TENSOR;

    return (jint)NATIVE_MESSAGE_SUCCESS;
}

JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_invoke(JNIEnv *env, jobject thiz, jobject in,
                                                            jlong in_size, jobject out,
                                                            jlong out_size) {
    if(TfLiteTensorByteSize(input_tensor) != in_size)
        return (jint)NATIVE_MESSAGE_ERROR_INVALID_SIZE;

    if(TfLiteTensorByteSize(output_tensor) != out_size)
        return (jint)NATIVE_MESSAGE_ERROR_INVALID_SIZE;

    void *inPointer = (*env)->GetDirectBufferAddress(env, in);
    void *outPointer = (*env)->GetDirectBufferAddress(env, out);


    if(TfLiteTensorCopyFromBuffer(input_tensor, inPointer, (size_t)in_size) != 0)
         return (jint)NATIVE_MESSAGE_ERROR_COPYBUFFER;

    TfLiteInterpreterInvoke(interpreter);

    if(TfLiteTensorCopyToBuffer(output_tensor, outPointer, (size_t)out_size) != 0)
        return (jint)NATIVE_MESSAGE_ERROR_COPYBUFFER;

    return (jint)NATIVE_MESSAGE_SUCCESS;
}

JNIEXPORT jint JNICALL
Java_it_unibo_cvlab_pydnet_TensorflowLiteModel_deleteInterpreter(JNIEnv *env, jobject thiz) {

    if(interpreter != NULL){
        TfLiteInterpreterDelete(interpreter);
        interpreter = NULL;
    }

    if(options != NULL){
        TfLiteInterpreterOptionsDelete(options);
        options = NULL;
    }

    if(model != NULL){
        TfLiteModelDelete(model);
        model = NULL;
    }

    input_tensor = NULL;
    output_tensor = NULL;

    return (jint)NATIVE_MESSAGE_SUCCESS;
}