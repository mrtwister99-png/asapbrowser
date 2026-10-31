use adblock::lists::{FilterSet, ParseOptions};
use adblock::Engine;
use adblock::request::Request;
use once_cell::sync::OnceCell;
use std::collections::HashSet;
use std::sync::Mutex;

struct EngineWrapper(Engine);
unsafe impl Send for EngineWrapper {}
unsafe impl Sync for EngineWrapper {}

static ENGINE: OnceCell<Mutex<EngineWrapper>> = OnceCell::new();

pub fn init(list_text: &str) {
    let mut filter_set = FilterSet::new(false);
    filter_set.add_filter_list(list_text, ParseOptions::default());
    let engine = Engine::from_filter_set(filter_set, true);
    let _ = ENGINE.set(Mutex::new(EngineWrapper(engine)));
}

pub fn should_block(url: &str, source_url: &str, resource_type: &str) -> bool {
    if let Some(cell) = ENGINE.get() {
        if let Ok(wrapper) = cell.lock() {
            let Ok(request) = Request::new(url, source_url, resource_type) else { return false; };
            let result = wrapper.0.check_network_request(&request);
            return result.matched;
        }
    }
    false
}

// ---------------- KOSMETICKE FILTRY (skryvani reklam na strance) ----------------

// Kazdy selektor dostane vlastni pravidlo: jeden vadny selektor tak nezrusi ostatni.
// Proceduralni selektory (JSON, zacinaji "{") se v prostem CSS nedaji pouzit - preskoci se.
fn selectors_to_css<'a>(it: impl Iterator<Item = &'a String>) -> String {
    let mut out = String::new();
    for sel in it {
        if sel.is_empty() || sel.starts_with('{') { continue; }
        out.push_str(sel);
        out.push_str("{display:none!important}\n");
    }
    out
}

// CSS pro skryti reklam platne pro danou adresu (selektory EasyListu pro domenu).
// Zacina-li vysledek "/*generichide*/", stranka si vyzadala vypnuti obecnych pravidel.
pub fn cosmetic_css(url: &str) -> String {
    let Some(cell) = ENGINE.get() else { return String::new(); };
    let Ok(wrapper) = cell.lock() else { return String::new(); };
    let res = wrapper.0.url_cosmetic_resources(url);
    let mut css = String::new();
    if res.generichide { css.push_str("/*generichide*/\n"); }
    css.push_str(&selectors_to_css(res.hide_selectors.iter()));
    css
}

// Obecna pravidla: stranka posle tridy a id svych prvku (oddelene novym radkem),
// vrati se CSS jen pro ty, ktere v EasyListu opravdu jsou.
pub fn hidden_selectors(classes: &str, ids: &str) -> String {
    let Some(cell) = ENGINE.get() else { return String::new(); };
    let Ok(wrapper) = cell.lock() else { return String::new(); };
    let classes: Vec<String> = classes.lines().map(|s| s.trim()).filter(|s| !s.is_empty()).map(|s| s.to_string()).collect();
    let ids: Vec<String> = ids.lines().map(|s| s.trim()).filter(|s| !s.is_empty()).map(|s| s.to_string()).collect();
    if classes.is_empty() && ids.is_empty() { return String::new(); }
    let exceptions: HashSet<String> = HashSet::new();
    let sels: Vec<String> = wrapper.0.hidden_class_id_selectors(&classes, &ids, &exceptions);
    selectors_to_css(sels.iter())
}

pub fn clean_url(url: &str) -> String {
    const TRACKING_PARAMS: &[&str] = &["utm_source","utm_medium","utm_campaign","utm_term","utm_content","fbclid","gclid","msclkid","mc_eid","igshid","ttclid"];
    let Ok(mut parsed) = url::Url::parse(url) else { return url.to_string() };
    let filtered: Vec<(String,String)> = parsed.query_pairs().filter(|(k,_)| !TRACKING_PARAMS.contains(&k.as_ref())).map(|(k,v)| (k.into_owned(), v.into_owned())).collect();
    if filtered.is_empty() { parsed.set_query(None); } else { let q = filtered.iter().map(|(k,v)| format!("{k}={v}")).collect::<Vec<_>>().join("&"); parsed.set_query(Some(&q)); }
    parsed.to_string()
}
