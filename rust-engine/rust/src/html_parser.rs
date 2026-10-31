//! Zjednodušený HTML5 tokenizer/parser -> dom::Node strom.
//! Pozn.: pro produkci zvážit napojení na html5ever, tady vlastní
//! odlehčená implementace kvůli velikosti .so (cíl: menší binárka).

use crate::dom::Node;
use std::collections::HashMap;

pub fn parse(html: &str) -> Node {
    let mut root = Node { node_type: crate::dom::NodeType::Document, children: vec![] };
    let mut stack: Vec<Node> = vec![];
    let mut chars = html.char_indices().peekable();
    let mut text_buf = String::new();

    while let Some((_, c)) = chars.next() {
        if c == '<' {
            flush_text(&mut text_buf, &mut stack);
            let mut tag_buf = String::new();
            let mut closing = false;
            if chars.peek().map(|(_, c)| *c) == Some('/') {
                closing = true;
                chars.next();
            }
            while let Some((_, c2)) = chars.peek() {
                if *c2 == '>' { chars.next(); break; }
                tag_buf.push(*c2);
                chars.next();
            }
            if closing {
                if let Some(node) = stack.pop() {
                    push_to_parent(&mut stack, &mut root, node);
                }
            } else {
                let (tag, attrs) = parse_tag(&tag_buf);
                let self_closing = tag_buf.trim_end().ends_with('/');
                let node = Node::element(&tag, attrs);
                if self_closing || is_void_element(&tag) {
                    push_to_parent(&mut stack, &mut root, node);
                } else {
                    stack.push(node);
                }
            }
        } else {
            text_buf.push(c);
        }
    }
    flush_text(&mut text_buf, &mut stack);
    while let Some(node) = stack.pop() {
        push_to_parent(&mut stack, &mut root, node);
    }
    root
}

fn flush_text(buf: &mut String, stack: &mut Vec<Node>) {
    if !buf.trim().is_empty() {
        if let Some(parent) = stack.last_mut() {
            parent.children.push(Node::text(buf.trim()));
        }
    }
    buf.clear();
}

fn push_to_parent(stack: &mut Vec<Node>, root: &mut Node, node: Node) {
    if let Some(parent) = stack.last_mut() {
        parent.children.push(node);
    } else {
        root.children.push(node);
    }
}

fn parse_tag(raw: &str) -> (String, HashMap<String, String>) {
    let raw = raw.trim().trim_end_matches('/').trim();
    let mut parts = raw.splitn(2, char::is_whitespace);
    let tag = parts.next().unwrap_or("").to_lowercase();
    let mut attrs = HashMap::new();
    if let Some(rest) = parts.next() {
        // velmi zjednodušený parser atributů key="value"
        let mut key = String::new();
        let mut val = String::new();
        let mut in_val = false;
        let mut in_quotes = false;
        for c in rest.chars() {
            match c {
                '=' if !in_val => { in_val = true; }
                '"' => { in_quotes = !in_quotes; }
                ' ' if !in_quotes => {
                    if !key.trim().is_empty() {
                        attrs.insert(key.trim().to_string(), val.trim().to_string());
                    }
                    key.clear(); val.clear(); in_val = false;
                }
                _ => if in_val { val.push(c) } else { key.push(c) },
            }
        }
        if !key.trim().is_empty() {
            attrs.insert(key.trim().to_string(), val.trim().to_string());
        }
    }
    (tag, attrs)
}

fn is_void_element(tag: &str) -> bool {
    matches!(tag, "img" | "br" | "hr" | "meta" | "link" | "input" | "source" | "track")
}
