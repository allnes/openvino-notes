#pragma once

#include <memory>
#include <string>

namespace notes::ai {

class LlmEngine {
public:
    LlmEngine();
    ~LlmEngine();

    void init(const std::string& model_dir, const std::string& cache_dir, const std::string& device);
    std::string generate(const std::string& prompt, int max_new_tokens);
    void close();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace notes::ai
