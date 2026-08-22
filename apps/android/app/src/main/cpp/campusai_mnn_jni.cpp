#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "llm/llm.hpp"

using MNN::Transformer::ChatMessages;
using MNN::Transformer::Llm;
using MNN::Transformer::LlmStatus;

namespace {
struct Engine {
    Llm* llm = nullptr;
    std::mutex mutex;
    std::atomic<bool> cancelled{false};
    ~Engine() { if (llm) Llm::destroy(llm); }
};

std::string toUtf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring fromUtf8(JNIEnv* env, const std::string& input) {
    std::vector<jchar> output;
    output.reserve(input.size());
    for (size_t index = 0; index < input.size();) {
        uint32_t codepoint = 0xfffd;
        const unsigned char first = static_cast<unsigned char>(input[index]);
        size_t count = 1;
        if (first < 0x80) codepoint = first;
        else if ((first & 0xe0) == 0xc0 && index + 1 < input.size()) { codepoint = first & 0x1f; count = 2; }
        else if ((first & 0xf0) == 0xe0 && index + 2 < input.size()) { codepoint = first & 0x0f; count = 3; }
        else if ((first & 0xf8) == 0xf0 && index + 3 < input.size()) { codepoint = first & 0x07; count = 4; }
        for (size_t i = 1; i < count; ++i) codepoint = (codepoint << 6) | (static_cast<unsigned char>(input[index + i]) & 0x3f);
        if (codepoint <= 0xffff) output.push_back(static_cast<jchar>(codepoint));
        else {
            codepoint -= 0x10000;
            output.push_back(static_cast<jchar>(0xd800 + (codepoint >> 10)));
            output.push_back(static_cast<jchar>(0xdc00 + (codepoint & 0x3ff)));
        }
        index += count;
    }
    return env->NewString(output.data(), static_cast<jsize>(output.size()));
}

void throwState(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeCreate(JNIEnv* env, jobject, jstring configPath, jstring cachePath) {
    auto engine = std::make_unique<Engine>();
    engine->llm = Llm::createLLM(toUtf8(env, configPath));
    if (!engine->llm) { throwState(env, "MNN could not create the model instance"); return 0; }
    const std::string cache = toUtf8(env, cachePath);
    const std::string config = std::string("{\"backend_type\":\"cpu\",\"thread_num\":4,\"precision\":\"low\",\"memory\":\"low\",\"async\":false,\"reuse_kv\":false,\"use_mmap\":true,\"tmp_path\":\"") + cache + "\",\"max_all_tokens\":4096,\"max_new_tokens\":512,\"jinja\":{\"context\":{\"enable_thinking\":false}}}";
    if (!engine->llm->set_config(config) || !engine->llm->load()) {
        throwState(env, "MNN failed to load the verified model");
        return 0;
    }
    return reinterpret_cast<jlong>(engine.release());
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeGenerate(
    JNIEnv* env, jobject, jlong pointer, jobjectArray roles, jobjectArray contents, jint maxTokens, jobject listener) {
    auto* engine = reinterpret_cast<Engine*>(pointer);
    if (!engine || !engine->llm) { throwState(env, "Local model is not loaded"); return nullptr; }
    const jsize count = env->GetArrayLength(roles);
    if (count != env->GetArrayLength(contents) || count < 1 || count > 32) { throwState(env, "Invalid local conversation"); return nullptr; }
    ChatMessages messages;
    size_t inputBytes = 0;
    for (jsize i = 0; i < count; ++i) {
        auto roleObject = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto contentObject = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        std::string role = toUtf8(env, roleObject);
        std::string content = toUtf8(env, contentObject);
        env->DeleteLocalRef(roleObject);
        env->DeleteLocalRef(contentObject);
        if (role != "system" && role != "user" && role != "assistant") { throwState(env, "Invalid local message role"); return nullptr; }
        inputBytes += content.size();
        if (inputBytes > 48000) { throwState(env, "Local context is too long; keep it within 4096 tokens"); return nullptr; }
        messages.emplace_back(std::move(role), std::move(content));
    }
    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onToken = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)Z");
    if (!onToken) { throwState(env, "Missing local token callback"); return nullptr; }

    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->cancelled.store(false);
    engine->llm->reset();
    const auto started = std::chrono::steady_clock::now();
    engine->llm->response(messages, nullptr, nullptr, 0);
    auto context = engine->llm->getContext();
    const int limit = std::max(1, std::min(static_cast<int>(maxTokens), 512));
    int emitted = 0;
    while (!engine->cancelled.load() && !engine->llm->stoped() && emitted < limit) {
        engine->llm->generate(1);
        context = engine->llm->getContext();
        if (!context || context->status == LlmStatus::INTERNAL_ERROR || context->status == LlmStatus::TIMEOUT) break;
        if (engine->llm->is_stop(context->current_token)) break;
        const std::string token = engine->llm->tokenizer_decode(context->current_token);
        if (!token.empty()) {
            jstring javaToken = fromUtf8(env, token);
            const jboolean stop = env->CallBooleanMethod(listener, onToken, javaToken);
            env->DeleteLocalRef(javaToken);
            if (env->ExceptionCheck() || stop == JNI_TRUE) { engine->cancelled.store(true); break; }
        }
        ++emitted;
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - started).count();
    const jlong values[6] = {
        context ? context->prompt_len : 0,
        context ? context->gen_seq_len : emitted,
        context ? context->prefill_us : 0,
        context ? context->decode_us : 0,
        context ? context->ttfa_us : 0,
        elapsed,
    };
    jlongArray result = env->NewLongArray(6);
    env->SetLongArrayRegion(result, 0, 6, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeCancel(JNIEnv*, jobject, jlong pointer) {
    auto* engine = reinterpret_cast<Engine*>(pointer);
    if (engine) engine->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeRelease(JNIEnv*, jobject, jlong pointer) {
    auto* engine = reinterpret_cast<Engine*>(pointer);
    if (!engine) return;
    engine->cancelled.store(true);
    {
        std::lock_guard<std::mutex> lock(engine->mutex);
    }
    delete engine;
}
