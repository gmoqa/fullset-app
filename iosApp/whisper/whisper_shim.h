// Shim C plano sobre whisper.cpp para iOS: expone funciones simples (sin structs por valor, que son
// fiddly desde Kotlin/Native). Equivale al whisper_jni.c de Android pero sin JNI — lo llama el
// cinterop `whispercpp` directamente. El audio es PCM mono 16 kHz normalizado a float -1..1.
#ifndef FULLSET_WHISPER_SHIM_H
#define FULLSET_WHISPER_SHIM_H

#ifdef __cplusplus
extern "C" {
#endif

// Carga el modelo ggml; devuelve un contexto opaco, o NULL si falla.
void *fullset_whisper_init(const char *model_path);

void fullset_whisper_free(void *ctx);

// Transcribe. `lang` es ISO ("es", "en"…) o "auto". Devuelve 0 si salió bien.
int fullset_whisper_transcribe(void *ctx, const float *audio, int n_samples, const char *lang, int threads);

int fullset_whisper_n_segments(void *ctx);

const char *fullset_whisper_segment_text(void *ctx, int index);

#ifdef __cplusplus
}
#endif

#endif
