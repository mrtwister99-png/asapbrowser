use crate::{adblock, engine_version, youtube_adblock, JS_ENGINES};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong, jstring, JNI_TRUE, JNI_FALSE};
use jni::JNIEnv;

// CORRECT package: cz.tomoweb.browser.engine.RustBridge

#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_engineVersion(env: JNIEnv, _class: JClass) -> jstring {
    env.new_string(engine_version()).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_initAdblock(mut env: JNIEnv, _class: JClass, list_text: JString) {
    let text: String = env.get_string(&list_text).unwrap().into();
    adblock::init(&text);
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_shouldBlock(mut env: JNIEnv, _class: JClass, url: JString, source_url: JString, resource_type: JString) -> jboolean {
    let url_str: String = env.get_string(&url).unwrap().into();
    let source_str: String = env.get_string(&source_url).unwrap().into();
    let rtype_str: String = env.get_string(&resource_type).unwrap().into();
    if adblock::should_block(&url_str, &source_str, &rtype_str) { JNI_TRUE } else { JNI_FALSE }
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_cleanUrl(mut env: JNIEnv, _class: JClass, url: JString) -> jstring {
    let url_str: String = env.get_string(&url).unwrap().into();
    let cleaned = adblock::clean_url(&url_str);
    env.new_string(cleaned).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockCss(env: JNIEnv, _class: JClass) -> jstring {
    env.new_string(youtube_adblock::get_css()).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockScript(env: JNIEnv, _class: JClass) -> jstring {
    env.new_string(youtube_adblock::get_js()).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockFull(env: JNIEnv, _class: JClass) -> jstring {
    env.new_string(youtube_adblock::get_full_script()).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_initJsEngineForTab(mut env: JNIEnv, _class: JClass, tab_id: JString, enabled: jboolean) -> jstring {
    let tab_id_str: String = env.get_string(&tab_id).unwrap().into();
    let result = JS_ENGINES.get_or_create(&tab_id_str, enabled == JNI_TRUE);
    let out = result.err().unwrap_or_default();
    env.new_string(out).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_evalJs(mut env: JNIEnv, _class: JClass, tab_id: JString, code: JString, url: JString) -> jstring {
    let tab_id_str: String = env.get_string(&tab_id).unwrap().into();
    let code_str: String = env.get_string(&code).unwrap().into();
    let url_str: String = env.get_string(&url).unwrap().into();
    let result = JS_ENGINES.eval(&tab_id_str, &code_str, &url_str);
    let json = match result {
        Ok(value) => format!(r#"{{"ok":true,"value":{}}}"#, value),
        Err(err) => {
            let err_str = serde_json::to_string(&err).unwrap_or_else(|_| "\"unknown\"".to_string());
            format!(r#"{{"ok":false,"error":{}}}"#, err_str)
        }
    };
    env.new_string(json).unwrap().into_raw()
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_destroyJsEngineForTab(mut env: JNIEnv, _class: JClass, tab_id: JString) {
    let tab_id_str: String = env.get_string(&tab_id).unwrap().into();
    JS_ENGINES.destroy(&tab_id_str);
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_browser_engine_RustBridge_antiFingerprintScript(env: JNIEnv, _class: JClass, profile_id: jlong, day_epoch: jlong) -> jstring {
    let noise = crate::security::anti_fingerprint::FingerprintNoise::new(profile_id, day_epoch);
    env.new_string(noise.js_shim()).unwrap().into_raw()
}

// Backward compatibility - old packages
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_engineVersion(env: JNIEnv, _class: JClass) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_engineVersion(env, _class)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_initAdblock(env: JNIEnv, _class: JClass, list_text: JString) {
    Java_cz_tomoweb_browser_engine_RustBridge_initAdblock(env, _class, list_text)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_shouldBlock(env: JNIEnv, _class: JClass, a: JString, b: JString, c: JString) -> jboolean {
    Java_cz_tomoweb_browser_engine_RustBridge_shouldBlock(env, _class, a, b, c)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_cleanUrl(env: JNIEnv, _class: JClass, a: JString) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_cleanUrl(env, _class, a)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_youtubeAdblockCss(env: JNIEnv, _class: JClass) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockCss(env, _class)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_youtubeAdblockScript(env: JNIEnv, _class: JClass) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockScript(env, _class)
}
#[no_mangle]
pub extern "system" fn Java_cz_tomoweb_engine_RustBridge_youtubeAdblockFull(env: JNIEnv, _class: JClass) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockFull(env, _class)
}
#[no_mangle]
pub extern "system" fn Java_com_myytbrowser_app_engine_RustBridge_youtubeAdblockFull(env: JNIEnv, _class: JClass) -> jstring {
    Java_cz_tomoweb_browser_engine_RustBridge_youtubeAdblockFull(env, _class)
}