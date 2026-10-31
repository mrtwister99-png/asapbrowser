#!/usr/bin/env bash
# Zkompiluje Rust core pro arm64-v8a, armeabi-v7a a x86_64 a zkopiruje .so do app/src/main/jniLibs.
# Vyzaduje: cargo install cargo-ndk ; rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
set -e
cd "$(dirname "$0")"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
echo "Hotovo: app/src/main/jniLibs/*/libmyytbrowser_engine.so"
