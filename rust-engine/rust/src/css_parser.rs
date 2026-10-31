//! Zjednodušený CSS parser: selektory + deklarace, bez media queries/animací (fáze 2).

#[derive(Debug, Clone)]
pub struct Declaration {
    pub property: String,
    pub value: String,
}

#[derive(Debug, Clone)]
pub struct Rule {
    pub selectors: Vec<String>, // "#id", ".class", "tag" - kombinátory zatím ne
    pub declarations: Vec<Declaration>,
}

pub struct Stylesheet {
    pub rules: Vec<Rule>,
}

pub fn parse(css: &str) -> Stylesheet {
    let mut rules = vec![];
    let mut rest = css;

    while let Some(open) = rest.find('{') {
        let selector_part = &rest[..open];
        let close = match rest[open..].find('}') {
            Some(c) => open + c,
            None => break,
        };
        let body = &rest[open + 1..close];

        let selectors = selector_part
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect();

        let declarations = body
            .split(';')
            .filter_map(|decl| {
                let mut parts = decl.splitn(2, ':');
                let prop = parts.next()?.trim();
                let val = parts.next()?.trim();
                if prop.is_empty() { return None; }
                Some(Declaration { property: prop.to_string(), value: val.to_string() })
            })
            .collect();

        rules.push(Rule { selectors, declarations });
        rest = &rest[close + 1..];
    }

    Stylesheet { rules }
}
