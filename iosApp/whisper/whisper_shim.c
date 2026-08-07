#include "whisper.h"
#include "whisper_shim.h"

void *fullset_whisper_init(const char *model_path) {
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // backend CPU (como Android); sin Metal
    return whisper_init_from_file_with_params(model_path, cparams);
}

void fullset_whisper_free(void *ctx) {
    if (ctx) whisper_free((struct whisper_context *) ctx);
}

int fullset_whisper_transcribe(void *ctx_, const float *audio, int n_samples, const char *lang, int threads) {
    struct whisper_context *ctx = (struct whisper_context *) ctx_;
    if (!ctx) return -1;

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false; // transcribir en el idioma hablado, no traducir
    params.language         = lang;
    params.n_threads        = threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;

    whisper_reset_timings(ctx);
    return whisper_full(ctx, params, audio, n_samples);
}

int fullset_whisper_n_segments(void *ctx) {
    return ctx ? whisper_full_n_segments((struct whisper_context *) ctx) : 0;
}

const char *fullset_whisper_segment_text(void *ctx, int index) {
    if (!ctx) return "";
    const char *text = whisper_full_get_segment_text((struct whisper_context *) ctx, index);
    return text ? text : "";
}
