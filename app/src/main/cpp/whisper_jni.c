// Binding JNI mínimo sobre whisper.cpp para transcribir notas de voz en el dispositivo.
//
// Adaptado del ejemplo oficial (examples/whisper.android) con dos cambios propios:
//  - el paquete apunta a com.gmoqa.fullset.data.WhisperLib
//  - `fullTranscribe` recibe el **idioma** como parámetro (el oficial lo dejaba fijo en "en"),
//    que es lo que permite dictar las notas en español desde Settings.

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define UNUSED(x) (void)(x)
#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_gmoqa_fullset_data_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *model_path = (*env)->GetStringUTFChars(env, model_path_str, NULL);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // compilamos solo el backend CPU para Android

    struct whisper_context *context = whisper_init_from_file_with_params(model_path, cparams);
    if (context == NULL) {
        LOGW("Could not load the model at %s", model_path);
    }
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_gmoqa_fullset_data_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (context != NULL) {
        whisper_free(context);
    }
}

/**
 * Corre la transcripción sobre `audio_data` (PCM mono 16 kHz normalizado a float -1..1).
 * `language_str` es un código ISO ("es", "en"…) o "auto" para que Whisper lo detecte.
 * Devuelve 0 si salió bien.
 */
JNIEXPORT jint JNICALL
Java_com_gmoqa_fullset_data_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (context == NULL) {
        return -1;
    }

    jfloat *audio = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_len = (*env)->GetArrayLength(env, audio_data);
    const char *language = (*env)->GetStringUTFChars(env, language_str, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false; // transcribir en el idioma hablado, no traducir
    params.language         = language;
    params.n_threads        = num_threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, audio, audio_len);
    if (result != 0) {
        LOGW("whisper_full failed with code %d", result);
    }

    (*env)->ReleaseStringUTFChars(env, language_str, language);
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio, JNI_ABORT);
    return (jint) result;
}

JNIEXPORT jint JNICALL
Java_com_gmoqa_fullset_data_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (context == NULL) {
        return 0;
    }
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_gmoqa_fullset_data_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    return (*env)->NewStringUTF(env, text == NULL ? "" : text);
}
