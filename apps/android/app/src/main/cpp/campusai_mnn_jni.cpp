#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <limits.h>
#include <memory>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <unordered_map>
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

std::mutex enginesMutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> engines;
std::atomic<jlong> nextEngineHandle{1};

std::shared_ptr<Engine> findEngine(jlong handle) {
    if (handle == 0) return {};
    std::lock_guard<std::mutex> lock(enginesMutex);
    const auto found = engines.find(handle);
    return found == engines.end() ? std::shared_ptr<Engine>{} : found->second;
}

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
        const unsigned char first = static_cast<unsigned char>(input[index]);
        size_t count = first < 0x80 ? 1 :
            (first & 0xe0) == 0xc0 ? 2 :
            (first & 0xf0) == 0xe0 ? 3 :
            (first & 0xf8) == 0xf0 ? 4 : 0;
        bool valid = count > 0 && index + count <= input.size();
        uint32_t codepoint = count == 1 ? first :
            count == 2 ? first & 0x1f :
            count == 3 ? first & 0x0f :
            count == 4 ? first & 0x07 : 0xfffd;
        for (size_t i = 1; valid && i < count; ++i) {
            const unsigned char continuation = static_cast<unsigned char>(input[index + i]);
            valid = (continuation & 0xc0) == 0x80;
            if (valid) codepoint = (codepoint << 6) | (continuation & 0x3f);
        }
        const bool overlong = (count == 2 && codepoint < 0x80) ||
            (count == 3 && codepoint < 0x800) ||
            (count == 4 && codepoint < 0x10000);
        if (!valid || overlong || codepoint > 0x10ffff || (codepoint >= 0xd800 && codepoint <= 0xdfff)) {
            output.push_back(static_cast<jchar>(0xfffd));
            ++index;
            continue;
        }
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

// MNN tokenizer pieces are byte strings, not guaranteed Unicode scalar
// boundaries. Keep a valid but incomplete trailing sequence for the next
// token instead of turning every fragment into U+FFFD.
size_t completeUtf8PrefixLength(const std::string& input) {
    size_t index = 0;
    while (index < input.size()) {
        const unsigned char first = static_cast<unsigned char>(input[index]);
        const size_t count = first < 0x80 ? 1 :
            (first & 0xe0) == 0xc0 ? 2 :
            (first & 0xf0) == 0xe0 ? 3 :
            (first & 0xf8) == 0xf0 ? 4 : 0;
        if (count == 0) {
            ++index;
            continue;
        }
        if (index + count > input.size()) {
            bool incomplete = true;
            for (size_t i = index + 1; i < input.size(); ++i) {
                if ((static_cast<unsigned char>(input[i]) & 0xc0) != 0x80) {
                    incomplete = false;
                    break;
                }
            }
            if (incomplete) return index;
            ++index;
            continue;
        }
        bool validContinuation = true;
        for (size_t i = 1; i < count; ++i) {
            if ((static_cast<unsigned char>(input[index + i]) & 0xc0) != 0x80) {
                validContinuation = false;
                break;
            }
        }
        index += validContinuation ? count : 1;
    }
    return input.size();
}

void throwState(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}

void throwVision(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type) env->ThrowNew(type, message);
}

bool containsRawMediaTag(const std::string& input) {
    std::string lowered;
    lowered.reserve(input.size());
    std::transform(input.begin(), input.end(), std::back_inserter(lowered), [](unsigned char value) {
        return static_cast<char>(std::tolower(value));
    });
    return lowered.find("<img") != std::string::npos ||
        lowered.find("</img") != std::string::npos ||
        lowered.find("<audio") != std::string::npos ||
        lowered.find("</audio") != std::string::npos ||
        lowered.find("<video") != std::string::npos ||
        lowered.find("</video") != std::string::npos;
}

bool canonicalFileInside(const std::string& rawPath, const std::string& rawRoot, std::string& canonicalPath) {
    char rootBuffer[PATH_MAX];
    char pathBuffer[PATH_MAX];
    if (!realpath(rawRoot.c_str(), rootBuffer) || !realpath(rawPath.c_str(), pathBuffer)) return false;
    struct stat fileInfo {};
    if (stat(pathBuffer, &fileInfo) != 0 || !S_ISREG(fileInfo.st_mode)) return false;
    const std::string root(rootBuffer);
    canonicalPath.assign(pathBuffer);
    return canonicalPath.size() > root.size() &&
        canonicalPath.compare(0, root.size(), root) == 0 &&
        canonicalPath[root.size()] == '/';
}

// The pinned Qwen3.5 MNN export ends its generation prompt with an open
// <think> block and does not consult jinja.context.enable_thinking.  MNN's
// runtime config therefore cannot disable reasoning for this model revision.
// Close only a trailing, still-empty think block before tokenization.  This
// keeps earlier assistant history intact while making the next turn start in
// final-answer mode.  Prompt content is never logged.
std::string forceFinalAnswerMode(std::string prompt) {
    const std::string marker = "<think>";
    const auto open = prompt.rfind(marker);
    if (open == std::string::npos) return prompt;
    const auto tailStart = open + marker.size();
    const bool emptyTail = std::all_of(
        prompt.begin() + static_cast<std::ptrdiff_t>(tailStart),
        prompt.end(),
        [](unsigned char value) { return std::isspace(value) != 0; }
    );
    if (!emptyTail) return prompt;
    prompt.erase(open);
    prompt.append("<think>\n\n</think>\n\n");
    return prompt;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeCreate(JNIEnv* env, jobject, jstring configPath, jstring cachePath) {
    auto engine = std::make_shared<Engine>();
    engine->llm = Llm::createLLM(toUtf8(env, configPath));
    if (!engine->llm) { throwState(env, "MNN could not create the model instance"); return 0; }
    const std::string cache = toUtf8(env, cachePath);
    const std::string config = std::string("{\"backend_type\":\"cpu\",\"thread_num\":4,\"precision\":\"low\",\"memory\":\"low\",\"async\":false,\"reuse_kv\":false,\"use_mmap\":true,\"tmp_path\":\"") + cache + "\",\"max_all_tokens\":8192,\"max_new_tokens\":512,\"jinja\":{\"context\":{\"enable_thinking\":false}}}";
    if (!engine->llm->set_config(config) || !engine->llm->load()) {
        throwState(env, "MNN failed to load the verified model");
        return 0;
    }
    const jlong handle = nextEngineHandle.fetch_add(1);
    {
        std::lock_guard<std::mutex> lock(enginesMutex);
        engines.emplace(handle, std::move(engine));
    }
    return handle;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeGenerate(
    JNIEnv* env,
    jobject,
    jlong pointer,
    jobjectArray roles,
    jobjectArray contents,
    jobjectArray imagePaths,
    jintArray imageMessageIndexes,
    jstring allowedImageRoot,
    jint maxTokens,
    jobject listener) {
    const auto engine = findEngine(pointer);
    if (!engine || !engine->llm) { throwState(env, "Local model is not loaded"); return nullptr; }
    const jsize count = env->GetArrayLength(roles);
    if (count != env->GetArrayLength(contents) || count < 1 || count > 32) { throwState(env, "Invalid local conversation"); return nullptr; }
    const jsize imageCount = env->GetArrayLength(imagePaths);
    if (imageCount != env->GetArrayLength(imageMessageIndexes) || imageCount > count * 4) {
        throwVision(env, "local_vision_decode_failed: invalid image ownership");
        return nullptr;
    }
    ChatMessages messages;
    messages.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto roleObject = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto contentObject = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        std::string role = toUtf8(env, roleObject);
        std::string content = toUtf8(env, contentObject);
        env->DeleteLocalRef(roleObject);
        env->DeleteLocalRef(contentObject);
        if (role != "system" && role != "user" && role != "assistant" && role != "tool") { throwState(env, "Invalid local message role"); return nullptr; }
        if (containsRawMediaTag(content)) {
            throwVision(env, "local_vision_decode_failed: raw media tags are not accepted");
            return nullptr;
        }
        messages.emplace_back(std::move(role), std::move(content));
    }
    if (imageCount > 0) {
        const std::string root = toUtf8(env, allowedImageRoot);
        if (root.empty()) { throwVision(env, "local_vision_decode_failed: missing private image root"); return nullptr; }
        std::vector<jint> owners(static_cast<size_t>(imageCount));
        env->GetIntArrayRegion(imageMessageIndexes, 0, imageCount, owners.data());
        if (env->ExceptionCheck()) return nullptr;
        std::vector<int> imageCounts(static_cast<size_t>(count), 0);
        std::vector<std::string> imagePrefixes(static_cast<size_t>(count));
        for (jsize i = 0; i < imageCount; ++i) {
            const jint owner = owners[static_cast<size_t>(i)];
            if (owner < 0 || owner >= count || messages[static_cast<size_t>(owner)].first != "user" || ++imageCounts[static_cast<size_t>(owner)] > 4) {
                throwVision(env, "local_vision_decode_failed: image is not owned by a user message");
                return nullptr;
            }
            auto pathObject = static_cast<jstring>(env->GetObjectArrayElement(imagePaths, i));
            const std::string rawPath = toUtf8(env, pathObject);
            env->DeleteLocalRef(pathObject);
            std::string canonicalPath;
            if (!canonicalFileInside(rawPath, root, canonicalPath)) {
                throwVision(env, "local_vision_decode_failed: image path escaped private storage");
                return nullptr;
            }
            imagePrefixes[static_cast<size_t>(owner)].append("<img>").append(canonicalPath).append("</img>");
        }
        for (jsize i = 0; i < count; ++i) {
            auto& prefix = imagePrefixes[static_cast<size_t>(i)];
            if (!prefix.empty()) messages[static_cast<size_t>(i)].second.insert(0, prefix + "\n");
        }
    }
    size_t inputBytes = 0;
    for (const auto& message : messages) {
        inputBytes += message.second.size();
        if (inputBytes > 96000) { throwState(env, "Local context is too long; keep it within 8192 tokens"); return nullptr; }
    }
    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onToken = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)Z");
    if (!onToken) { throwState(env, "Missing local token callback"); return nullptr; }

    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->cancelled.store(false);
    engine->llm->reset();
    const auto started = std::chrono::steady_clock::now();
    const std::string renderedPrompt = forceFinalAnswerMode(engine->llm->apply_chat_template(messages));
    const std::vector<int> inputIds = engine->llm->tokenizer_encode(renderedPrompt);
    if (inputIds.empty()) { throwState(env, "MNN produced an empty local prompt"); return nullptr; }
    engine->llm->response(inputIds, nullptr, nullptr, 0);
    auto context = engine->llm->getContext();
    const int limit = std::max(1, std::min(static_cast<int>(maxTokens), 512));
    int emitted = 0;
    std::string pendingUtf8;
    while (!engine->cancelled.load() && !engine->llm->stoped() && emitted < limit) {
        engine->llm->generate(1);
        context = engine->llm->getContext();
        if (!context || context->status == LlmStatus::INTERNAL_ERROR || context->status == LlmStatus::TIMEOUT) break;
        if (engine->llm->is_stop(context->current_token)) break;
        const std::string token = engine->llm->tokenizer_decode(context->current_token);
        if (!token.empty()) {
            pendingUtf8.append(token);
            const size_t readyBytes = completeUtf8PrefixLength(pendingUtf8);
            if (readyBytes > 0) {
                jstring javaToken = fromUtf8(env, pendingUtf8.substr(0, readyBytes));
                const jboolean stop = env->CallBooleanMethod(listener, onToken, javaToken);
                env->DeleteLocalRef(javaToken);
                pendingUtf8.erase(0, readyBytes);
                if (env->ExceptionCheck() || stop == JNI_TRUE) { engine->cancelled.store(true); break; }
            }
        }
        ++emitted;
    }
    if (!engine->cancelled.load() && !pendingUtf8.empty()) {
        throwState(env, "Local model returned incomplete UTF-8 output");
        return nullptr;
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - started).count();
    const jlong values[8] = {
        context ? context->prompt_len : 0,
        context ? context->gen_seq_len : emitted,
        context ? context->prefill_us : 0,
        context ? context->decode_us : 0,
        context ? context->ttfa_us : 0,
        elapsed,
        context ? context->vision_us : 0,
        context ? static_cast<jlong>(context->pixels_mp * 1000000.0f) : 0,
    };
    jlongArray result = env->NewLongArray(8);
    env->SetLongArrayRegion(result, 0, 8, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeCancel(JNIEnv*, jobject, jlong pointer) {
    const auto engine = findEngine(pointer);
    if (engine) engine->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_campusai_core_localai_MnnNativeBridge_nativeRelease(JNIEnv*, jobject, jlong pointer) {
    std::shared_ptr<Engine> engine;
    {
        std::lock_guard<std::mutex> lock(enginesMutex);
        const auto found = engines.find(pointer);
        if (found == engines.end()) return;
        engine = std::move(found->second);
        engines.erase(found);
    }
    engine->cancelled.store(true);
    {
        std::lock_guard<std::mutex> lock(engine->mutex);
    }
}
