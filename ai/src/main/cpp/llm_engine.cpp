#include "llm_engine.h"

#include <openvino/openvino.hpp>

#include "openvino/genai/llm_pipeline.hpp"

#include <limits>
#include <mutex>
#include <stdexcept>
#include <utility>

namespace notes::ai {

struct LlmEngine::Impl {
    explicit Impl(std::unique_ptr<ov::genai::LLMPipeline> llm_pipeline)
        : pipeline(std::move(llm_pipeline)) {}

    std::unique_ptr<ov::genai::LLMPipeline> pipeline;
    std::mutex mutex;
};

LlmEngine::LlmEngine() = default;

LlmEngine::~LlmEngine() {
    // OpenVINO GenAI Android teardown currently crashes after successful generation.
    // Keep the process-lifetime pipeline allocated instead of running its destructor.
    impl_.release();
}

void LlmEngine::init(
    const std::string& model_dir,
    const std::string& cache_dir,
    const std::string& device
) {
    if (model_dir.empty()) {
        throw std::invalid_argument("OpenVINO LLM model directory is empty.");
    }
    if (impl_) {
        return;
    }

    const std::string target_device = device.empty() ? "CPU" : device;
    ov::AnyMap pipeline_config;
    if (!cache_dir.empty()) {
        pipeline_config.insert({ov::cache_dir(cache_dir)});
    }
    pipeline_config.insert({ov::hint::inference_precision(ov::element::f32)});
    pipeline_config.insert({ov::hint::dynamic_quantization_group_size(std::numeric_limits<uint64_t>::max())});

    auto pipeline = std::make_unique<ov::genai::LLMPipeline>(model_dir, target_device, pipeline_config);
    impl_ = std::make_unique<Impl>(std::move(pipeline));
}

std::string LlmEngine::generate(const std::string& prompt, int max_new_tokens) {
    if (!impl_ || !impl_->pipeline) {
        throw std::logic_error("OpenVINO GenAI pipeline is not initialized.");
    }
    if (prompt.empty()) {
        return "";
    }

    std::lock_guard<std::mutex> lock(impl_->mutex);
    ov::genai::GenerationConfig generation_config = impl_->pipeline->get_generation_config();
    generation_config.max_new_tokens = static_cast<size_t>(max_new_tokens);
    generation_config.do_sample = false;
    return impl_->pipeline->generate(prompt, generation_config);
}

void LlmEngine::close() {
    // The process owns the GenAI pipeline for the same reason as the destructor above.
}

} // namespace notes::ai
