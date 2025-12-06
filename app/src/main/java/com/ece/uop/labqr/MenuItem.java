package com.ece.uop.labqr;

public class MenuItem {
    private int iconResId;
    private String text;

    public MenuItem(int iconResId, String text) {
        this.iconResId = iconResId;
        this.text = text;
    }

    public int getIconResId() {
        return iconResId;
    }

    public String getText() {
        return text;
    }
}

