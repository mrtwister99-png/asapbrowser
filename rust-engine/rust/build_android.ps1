# Spust v PowerShellu ve slozce rust-engine.
# Jednorazove pripravit:
#   cargo install cargo-ndk
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
Write-Host "Building rust-engine (arm64 + 32bit arm + x86_64)..." -ForegroundColor Green

# --% = PowerShell neparsuje -t a -o jako svoje parametry
cargo --% ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release

foreach ($abi in "arm64-v8a","armeabi-v7a","x86_64") {
    $f = "../app/src/main/jniLibs/$abi/libmyytbrowser_engine.so"
    if (Test-Path $f) { Write-Host "OK  $abi" -ForegroundColor Green } else { Write-Host "CHYBI  $abi" -ForegroundColor Red }
}
