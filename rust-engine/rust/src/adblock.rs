use adblock::lists::{FilterSet, ParseOptions};
use adblock::Engine;
use adblock::request::Request;
use once_cell::sync::OnceCell;
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

pub fn clean_url(url: &str) -> String {
    const TRACKING_PARAMS: &[&str] = &["utm_source","utm_medium","utm_campaign","utm_term","utm_content","fbclid","gclid","msclkid","mc_eid","igshid","ttclid"];
    let Ok(mut parsed) = url::Url::parse(url) else { return url.to_string() };
    let filtered: Vec<(String,String)> = parsed.query_pairs().filter(|(k,_)| !TRACKING_PARAMS.contains(&k.as_ref())).map(|(k,v)| (k.into_owned(), v.into_owned())).collect();
    if filtered.is_empty() { parsed.set_query(None); } else { let q = filtered.iter().map(|(k,v)| format!("{k}={v}")).collect::<Vec<_>>().join("&"); parsed.set_query(Some(&q)); }
    parsed.to_string()
}
