#include <jni.h>
#include "llama.h"
#include <vector>
#include <string>
#include <algorithm>

static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;
static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
extern "C" JNIEXPORT void JNICALL
Java_com_tannmenghong_tbchat_inference_LocalChat_close(JNIEnv *, jobject) {
    if (sampler) llama_sampler_free(sampler);
    if (ctx) llama_free(ctx);
    if (model) llama_model_free(model);
    sampler = nullptr; ctx = nullptr; model = nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_tannmenghong_tbchat_inference_LocalChat_open(JNIEnv *env, jobject self, jstring path) {
    Java_com_tannmenghong_tbchat_inference_LocalChat_close(env, self);
    llama_backend_init();
    auto p = llama_model_default_params(); p.n_gpu_layers = 0;
    const char *chars = env->GetStringUTFChars(path, nullptr);
    model = llama_model_load_from_file(chars, p);
    env->ReleaseStringUTFChars(path, chars);
    if (!model) fail(env, "Cannot load model. Check available memory and download integrity.");
}
extern "C" JNIEXPORT void JNICALL
Java_com_tannmenghong_tbchat_inference_LocalChat_begin(JNIEnv *env, jobject, jbyteArray prompt, jfloat temperature) {
    if (!model) { fail(env, "Load a model first."); return; }
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    const auto length = env->GetArrayLength(prompt);
    std::string text(length, '\0');
    env->GetByteArrayRegion(prompt, 0, length, reinterpret_cast<jbyte *>(text.data()));
    const auto *vocab = llama_model_get_vocab(model);
    int count = -llama_tokenize(vocab, text.data(), length, nullptr, 0, true, true);
    if (count <= 0 || count > 3072) { fail(env, "Conversation exceeds context limit. Start a new chat or shorten the message."); return; }
    std::vector<llama_token> tokens(count);
    llama_tokenize(vocab, text.data(), length, tokens.data(), count, true, true);
    auto p = llama_context_default_params(); p.n_ctx = 4096; p.n_batch = 512; p.n_threads = 4; p.n_threads_batch = 4;
    ctx = llama_init_from_model(model, p);
    if (!ctx) { fail(env, "Not enough memory for conversation."); return; }
    sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    for (int offset = 0; offset < count; offset += 512) {
        auto batch = llama_batch_get_one(tokens.data() + offset, std::min(512, count - offset));
        if (llama_decode(ctx, batch)) { fail(env, "Could not process conversation."); return; }
    }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tannmenghong_tbchat_inference_LocalChat_next(JNIEnv *env, jobject) {
    if (!ctx || !sampler) { fail(env, "Conversation is not prepared."); return nullptr; }
    const auto *vocab = llama_model_get_vocab(model);
    llama_token token = llama_sampler_sample(sampler, ctx, -1);
    if (llama_vocab_is_eog(vocab, token)) return nullptr;
    std::vector<char> bytes(256);
    int size = llama_token_to_piece(vocab, token, bytes.data(), bytes.size(), 0, false);
    if (size < 0) { bytes.resize(-size); size = llama_token_to_piece(vocab, token, bytes.data(), bytes.size(), 0, false); }
    auto batch = llama_batch_get_one(&token, 1);
    if (llama_decode(ctx, batch)) { fail(env, "Generation context is full."); return nullptr; }
    auto result = env->NewByteArray(size);
    env->SetByteArrayRegion(result, 0, size, reinterpret_cast<jbyte *>(bytes.data()));
    return result;
}
