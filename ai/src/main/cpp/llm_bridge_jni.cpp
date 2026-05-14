#include "llm_engine.h"

#include <jni.h>

#include <exception>
#include <mutex>
#include <string>

namespace {

notes::ai::LlmEngine g_engine;
std::mutex g_engine_mutex;

std::string to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throw_illegal_state(JNIEnv* env, const std::string& message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_itlab_ai_NativeLlmBridge_init(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring model_dir,
    jstring cache_dir,
    jstring device
) {
    try {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        g_engine.init(to_string(env, model_dir), to_string(env, cache_dir), to_string(env, device));
    } catch (const std::exception& error) {
        throw_illegal_state(env, error.what());
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_itlab_ai_NativeLlmBridge_generate(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring prompt,
    jint max_new_tokens
) {
    try {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        const std::string result = g_engine.generate(to_string(env, prompt), max_new_tokens);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& error) {
        throw_illegal_state(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_itlab_ai_NativeLlmBridge_close(JNIEnv* env, jobject /*thiz*/) {
    try {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        g_engine.close();
    } catch (const std::exception& error) {
        throw_illegal_state(env, error.what());
    }
}
