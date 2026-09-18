package me.johardt.heracles.client;

enum EditorTool {
    SELECT("move", "Move or select quest", "S"),
    HAND("drag", "Pan quest tree", "H"),
    ADD("add", "Add quest", "A"),
    LINK("link", "Link dependency; Shift-click the dependent to remove", "L");

    final String icon;
    final String tooltip;
    final String shortcut;

    EditorTool(String icon, String tooltip, String shortcut) {
        this.icon = icon;
        this.tooltip = tooltip;
        this.shortcut = shortcut;
    }
}
