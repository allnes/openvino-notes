[![CI](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/ci.yml?query=branch%3Amain)

# OpenVINO Notes

OpenVINO notes is an AI-powered notes app for Android. 

The project is written in **Kotlin** and uses **OpenVINO** to run AI features directly on Android devices.

## 🎯 Goal
Build a lightweight Android notes application with on-device AI for fast, private, and efficient text processing.

## 🚀 Build & Run

### Prerequisites
* Android Studio (Ladybug+)
* JDK 17
* Android SDK 34

### Terminal Instructions

1. Clone the repository:
git clone https://github.com/IntFxZen/openvino-notes.git

2. Build the project:
./gradlew assembleDebug

3. Install on device:
./gradlew installDebug

## 🏗 Architecture & Tech Stack

The project follows **Clean Architecture** principles with a multi-module setup:
* :app — UI & Dependency Injection
* :domain — Business Logic & Repository interfaces
* :data — Repository implementations & Storage
* :ai — OpenVINO Java API & AI logic

**Core Technologies:**
* Kotlin
* Android
* OpenVINO

## 📚 Documentation

Project documentation now lives in [`docs/`](./docs/README.md) as a small tree:

* [`docs/README.md`](./docs/README.md) - documentation index
* [`docs/developer/README.md`](./docs/developer/README.md) - contributor entry point
* [`docs/developer/project-tree.md`](./docs/developer/project-tree.md) - repository structure and CI layout
* [`docs/developer/ci-local.md`](./docs/developer/ci-local.md) - how to reproduce CI checks locally on Linux, macOS, and Windows
