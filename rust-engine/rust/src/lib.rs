
pub mod adblock;
pub mod js_engine;
pub mod network;
pub mod youtube_adblock;
pub mod bridge { pub mod jni_bridge; }
pub mod security { pub mod anti_fingerprint; }
pub mod dom;
pub mod html_parser;
pub mod css_parser;
pub mod style;
pub mod layout;
pub fn engine_version() -> &'static str { env!("CARGO_PKG_VERSION") }
use once_cell::sync::Lazy;
pub static JS_ENGINES: Lazy<js_engine::JsEngineRegistry> = Lazy::new(js_engine::JsEngineRegistry::new);
