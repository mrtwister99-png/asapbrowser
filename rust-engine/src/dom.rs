//! Minimální DOM reprezentace: strom uzlů (Element/Text), bez plné W3C API.

use std::collections::HashMap;

#[derive(Debug, Clone)]
pub enum NodeType {
    Document,
    Element { tag: String, attrs: HashMap<String, String> },
    Text(String),
    Comment(String),
}

#[derive(Debug, Clone)]
pub struct Node {
    pub node_type: NodeType,
    pub children: Vec<Node>,
}

impl Node {
    pub fn element(tag: &str, attrs: HashMap<String, String>) -> Self {
        Node { node_type: NodeType::Element { tag: tag.to_string(), attrs }, children: vec![] }
    }

    pub fn text(content: &str) -> Self {
        Node { node_type: NodeType::Text(content.to_string()), children: vec![] }
    }

    pub fn tag_name(&self) -> Option<&str> {
        match &self.node_type {
            NodeType::Element { tag, .. } => Some(tag),
            _ => None,
        }
    }

    pub fn attr(&self, name: &str) -> Option<&str> {
        match &self.node_type {
            NodeType::Element { attrs, .. } => attrs.get(name).map(|s| s.as_str()),
            _ => None,
        }
    }

    /// Prohledá podstrom podle CSS-like id selektoru (#id) - používá adblock.rs pro cookie-reject selektory.
    pub fn find_by_id<'a>(&'a self, id: &str) -> Option<&'a Node> {
        if self.attr("id") == Some(id) {
            return Some(self);
        }
        self.children.iter().find_map(|c| c.find_by_id(id))
    }
}
