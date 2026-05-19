package com.itlab.ai

data class OnDeviceVisionConfig(
    val assetModelDir: String,
    val modelDirName: String,
    val modelXmlFileName: String,
    val classNamesFileName: String,
    val manifestFileName: String,
    val runtimeAssetDir: String,
    val runtimeDirName: String,
    val javaApiLibraryName: String,
    val device: String,
    val inputSize: Int,
    val confidenceThreshold: Float,
    val iouThreshold: Float,
    val maxDetections: Int,
    val maxTags: Int,
) {
    companion object {
        fun defaultAndroid(): OnDeviceVisionConfig =
            OnDeviceVisionConfig(
                assetModelDir = "models/on-device-vision-openvino",
                modelDirName = "on-device-vision-openvino",
                modelXmlFileName = "yolo26n.xml",
                classNamesFileName = "coco.names",
                manifestFileName = "openvino_vision_manifest.json",
                runtimeAssetDir = "openvino-runtime",
                runtimeDirName = "openvino-runtime",
                javaApiLibraryName = "inference_engine_java_api",
                device = "CPU",
                inputSize = 640,
                confidenceThreshold = 0.35f,
                iouThreshold = 0.45f,
                maxDetections = 300,
                maxTags = 4,
            )
    }
}
