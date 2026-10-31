//! Zjednodušený block-flow layout (bez flex/grid - fáze 2). Počítá jen
//! obdélníky pro block elementy stacknuté pod sebou, dost pro reader-mode
//! rendering; skutečné pixely nakonec kreslí Compose/WebView vrstva.

use crate::style::StyledNode;

#[derive(Debug, Clone, Copy, Default)]
pub struct Rect {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

#[derive(Debug)]
pub struct LayoutBox {
    pub rect: Rect,
    pub children: Vec<LayoutBox>,
}

pub fn layout(styled: &StyledNode, containing_width: f32) -> LayoutBox {
    layout_block(styled, 0.0, 0.0, containing_width)
}

fn layout_block(styled: &StyledNode, x: f32, mut y: f32, width: f32) -> LayoutBox {
    let padding = parse_px(styled.properties.get("padding")).unwrap_or(0.0);
    let mut children = vec![];
    let mut cursor_y = y + padding;

    for child in &styled.children {
        let child_box = layout_block(child, x + padding, cursor_y, width - 2.0 * padding);
        cursor_y += child_box.rect.height;
        children.push(child_box);
    }

    let explicit_height = parse_px(styled.properties.get("height"));
    let height = explicit_height.unwrap_or(cursor_y - y + padding);

    LayoutBox { rect: Rect { x, y, width, height }, children }
}

fn parse_px(value: Option<&String>) -> Option<f32> {
    value?.trim_end_matches("px").parse::<f32>().ok()
}
