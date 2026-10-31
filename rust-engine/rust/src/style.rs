//! Spojuje dom::Node se css_parser::Stylesheet -> StyledNode strom pro layout.rs.

use crate::css_parser::Stylesheet;
use crate::dom::Node;
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct StyledNode<'a> {
    pub node: &'a Node,
    pub properties: HashMap<String, String>,
    pub children: Vec<StyledNode<'a>>,
}

pub fn style_tree<'a>(root: &'a Node, stylesheet: &Stylesheet) -> StyledNode<'a> {
    let properties = matched_properties(root, stylesheet);
    let children = root.children.iter().map(|c| style_tree(c, stylesheet)).collect();
    StyledNode { node: root, properties, children }
}

fn matched_properties(node: &Node, stylesheet: &Stylesheet) -> HashMap<String, String> {
    let mut props = HashMap::new();
    for rule in &stylesheet.rules {
        if rule.selectors.iter().any(|sel| selector_matches(node, sel)) {
            for decl in &rule.declarations {
                props.insert(decl.property.clone(), decl.value.clone());
            }
        }
    }
    props
}

fn selector_matches(node: &Node, selector: &str) -> bool {
    if let Some(id) = selector.strip_prefix('#') {
        return node.attr("id") == Some(id);
    }
    if let Some(class) = selector.strip_prefix('.') {
        return node.attr("class").map_or(false, |c| c.split_whitespace().any(|x| x == class));
    }
    node.tag_name() == Some(selector)
}
