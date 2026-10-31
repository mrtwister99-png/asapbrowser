//! Síťová vrstva enginu: auto-HTTPS upgrade + volitelné DNS-over-HTTPS.
//! Poznámka: primární síť appky jde přes Kotlin OkHttp (SecurityManager.kt);
//! toto je pro požadavky vycházející přímo z Rust enginu (např. prefetch).

use reqwest::Client;
use std::time::Duration;

pub struct NetworkConfig {
    pub doh_enabled: bool,
    pub doh_provider: DohProvider,
    pub auto_https_upgrade: bool,
}

pub enum DohProvider {
    Cloudflare, // https://cloudflare-dns.com/dns-query
    Quad9,      // https://dns.quad9.net/dns-query
}

impl DohProvider {
    pub fn endpoint(&self) -> &'static str {
        match self {
            DohProvider::Cloudflare => "https://cloudflare-dns.com/dns-query",
            DohProvider::Quad9 => "https://dns.quad9.net/dns-query",
        }
    }
}

pub fn build_client(config: &NetworkConfig) -> Client {
    Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .expect("reqwest client")
}

/// VRSTVA 6 bod 5: pokud je auto_https_upgrade zapnuté a URL je http://, přepiš na https://.
pub fn maybe_upgrade_https(url: &str, config: &NetworkConfig) -> String {
    if config.auto_https_upgrade && url.starts_with("http://") {
        format!("https://{}", &url["http://".len()..])
    } else {
        url.to_string()
    }
}
