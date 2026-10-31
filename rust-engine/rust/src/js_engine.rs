//! JS engine napojený na rquickjs (bezpečné Rust bindingy nad QuickJS).
//! Jeden JsEngine = jeden sandboxovaný kontext, vytváří se per tab (viz
//! komentář u `JsEngineRegistry` na konci souboru) - žádné sdílení globals
//! mezi taby, žádný přístup mezi profily.

use rquickjs::{Array, Context, Ctx, Object, Runtime, Value};
use std::collections::HashMap;
use std::sync::Mutex;

/// 64 MB - tvrdý limit paměti na JS kontext, aby jedna stránka nesestřelila celou appku.
const MEMORY_LIMIT_BYTES: usize = 64 * 1024 * 1024;
/// 1 MB stack - ochrana proti nekonečné rekurzi v nedůvěryhodném JS.
const MAX_STACK_SIZE_BYTES: usize = 1024 * 1024;

pub struct JsEngine {
    runtime: Runtime,
    context: Context,
    pub enabled: bool,
}

// rquickjs Runtime/Context obsahuje Rc + NonNull, takže není Send/Sync
// pro náš use-case (global registry per tab chráněný Mutexem) to je OK
unsafe impl Send for JsEngine {}
unsafe impl Sync for JsEngine {}

impl JsEngine {
    /// Vytvoří nový sandboxovaný JS kontext. `enabled` odpovídá VRSTVA 6 bodu 11
    /// (per-stránka JS toggle) - pokud false, eval/set_global rovnou vrací chybu
    /// a runtime se ani neinicializuje zbytečně draze.
    pub fn new(enabled: bool) -> Result<Self, String> {
        let runtime = Runtime::new().map_err(|e| format!("runtime_init_failed: {e}"))?;
        runtime.set_memory_limit(MEMORY_LIMIT_BYTES);
        runtime.set_max_stack_size(MAX_STACK_SIZE_BYTES);

        let context = Context::full(&runtime).map_err(|e| format!("context_init_failed: {e}"))?;

        let engine = JsEngine { runtime, context, enabled };
        if enabled {
            engine.apply_sandbox()?;
        }
        Ok(engine)
    }

    /// Odstraní/zablokuje nebezpečná nebo nepodporovaná API, aby se stránka
    /// nemohla dostat mimo sandbox kontextu. `importScripts` je Worker API,
    /// které v tomto enginu nemá smysl a mohlo by sloužit k obcházení adblocku
    /// (dynamické donačítání skriptů mimo kontrolu network vrstvy).
    fn apply_sandbox(&self) -> Result<(), String> {
        self.context
            .with(|ctx| -> rquickjs::Result<()> {
                ctx.eval::<(), _>(
                    r#"
                    delete globalThis.importScripts;
                    delete globalThis.WebAssembly;
                    delete globalThis.SharedArrayBuffer;
                    // fetch/XHR nejsou QuickJS nativní - explicitně necháváme nedefinované,
                    // veškerá síť jde přes Kotlin SecurityManager/OkHttp vrstvu.
                    "#,
                )
            })
            .map_err(|e| format!("sandbox_init_failed: {e}"))
    }

    /// Nastaví globální proměnnou v kontextu z JSON hodnoty (typicky data
    /// posílaná z Kotlin vrstvy - profil info, feature flags apod.).
    pub fn set_global(&self, name: &str, json_value: &str) -> Result<(), String> {
        if !self.enabled {
            return Err("js_disabled_for_page".to_string());
        }
        let parsed: serde_json::Value =
            serde_json::from_str(json_value).map_err(|e| format!("invalid_json: {e}"))?;

        self.context
            .with(|ctx| -> Result<(), String> {
                let js_val = json_to_js(ctx.clone(), &parsed).map_err(|e| format!("convert_failed: {e}"))?;
                ctx.globals()
                    .set(name, js_val)
                    .map_err(|e| format!("set_global_failed: {e}"))
            })
    }

    /// Vyhodnotí JS kód. `url` slouží jen k anotaci zdroje (přes `//# sourceURL=`
    /// konvenci, kterou QuickJS respektuje ve stack trace) pro čitelné chyby v logu.
    /// Vrací výsledek serializovaný do JSON stringu.
    pub fn eval(&self, code: &str, url: &str) -> Result<String, String> {
        if !self.enabled {
            return Err("js_disabled_for_page".to_string());
        }

        let annotated = format!("{code}\n//# sourceURL={url}");

        self.context.with(|ctx| {
            let result: rquickjs::Result<Value> = ctx.eval(annotated.into_bytes());
            match result {
                Ok(value) => js_to_json(ctx.clone(), &value)
                    .map(|v| v.to_string())
                    .map_err(|e| format!("{url}: serialize_failed: {e}")),
                Err(rquickjs::Error::Exception) => {
                    let exception = ctx.catch();
                    let message = exception
                        .as_exception()
                        .and_then(|e| e.message())
                        .unwrap_or_else(|| "unknown_js_exception".to_string());
                    Err(format!("{url}: {message}"))
                }
                Err(e) => Err(format!("{url}: {e}")),
            }
        })
    }
}

fn json_to_js<'js>(ctx: Ctx<'js>, value: &serde_json::Value) -> rquickjs::Result<Value<'js>> {
    Ok(match value {
        serde_json::Value::Null => Value::new_null(ctx),
        serde_json::Value::Bool(b) => Value::new_bool(ctx, *b),
        serde_json::Value::Number(n) => Value::new_number(ctx, n.as_f64().unwrap_or(0.0)),
        serde_json::Value::String(s) => rquickjs::String::from_str(ctx, s)?.into_value(),
        serde_json::Value::Array(items) => {
            let arr = Array::new(ctx.clone())?;
            for (i, item) in items.iter().enumerate() {
                arr.set(i, json_to_js(ctx.clone(), item)?)?;
            }
            arr.into_value()
        }
        serde_json::Value::Object(map) => {
            let obj = Object::new(ctx.clone())?;
            for (k, v) in map {
                obj.set(k.as_str(), json_to_js(ctx.clone(), v)?)?;
            }
            obj.into_value()
        }
    })
}

fn js_to_json<'js>(ctx: Ctx<'js>, value: &Value<'js>) -> rquickjs::Result<serde_json::Value> {
    use serde_json::Value as J;
    if value.is_null() || value.is_undefined() {
        return Ok(J::Null);
    }
    if let Some(b) = value.as_bool() {
        return Ok(J::Bool(b));
    }
    if let Some(f) = value.as_float() {
        return Ok(serde_json::Number::from_f64(f).map(J::Number).unwrap_or(J::Null));
    }
    if let Some(i) = value.as_int() {
        return Ok(J::Number(i.into()));
    }
    if let Some(s) = value.as_string() {
        return Ok(J::String(s.to_string()?));
    }
    if let Some(arr) = value.as_array() {
        let mut out = Vec::with_capacity(arr.len());
        for item in arr.iter::<Value>() {
            out.push(js_to_json(ctx.clone(), &item?)?);
        }
        return Ok(J::Array(out));
    }
    if let Some(obj) = value.as_object() {
        let mut map = serde_json::Map::new();
        for key in obj.keys::<String>() {
            let key = key?;
            let val: Value = obj.get(&key)?;
            map.insert(key, js_to_json(ctx.clone(), &val)?);
        }
        return Ok(J::Object(map));
    }
    // funkce/symboly apod. - nedají se smysluplně serializovat
    Ok(J::Null)
}

/// Registr JS kontextů per tab. Klíč je Tab.id (viz engine/EngineModels.kt).
/// Voláno z JNI mostu (bridge/jni_bridge.rs) - každý tab má svůj vlastní
/// JsEngine, takže stránka v jednom tabu nemůže číst globals jiného tabu.
pub struct JsEngineRegistry {
    engines: Mutex<HashMap<String, JsEngine>>,
}

unsafe impl Send for JsEngineRegistry {}
unsafe impl Sync for JsEngineRegistry {}

impl JsEngineRegistry {
    pub fn new() -> Self {
        JsEngineRegistry { engines: Mutex::new(HashMap::new()) }
    }

    pub fn get_or_create(&self, tab_id: &str, enabled: bool) -> Result<(), String> {
        let mut engines = self.engines.lock().unwrap();
        if !engines.contains_key(tab_id) {
            engines.insert(tab_id.to_string(), JsEngine::new(enabled)?);
        }
        Ok(())
    }

    pub fn eval(&self, tab_id: &str, code: &str, url: &str) -> Result<String, String> {
        let engines = self.engines.lock().unwrap();
        let engine = engines.get(tab_id).ok_or("engine_not_initialized")?;
        engine.eval(code, url)
    }

    pub fn destroy(&self, tab_id: &str) {
        self.engines.lock().unwrap().remove(tab_id);
    }
}