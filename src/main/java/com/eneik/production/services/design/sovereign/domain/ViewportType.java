package com.eneik.production.services.design.sovereign.domain;

/**
 * Mandatory viewports for hardware screenshot rendering and visual auditing.
 */
public enum ViewportType {
    DESKTOP_1440(1440, 900),
    MOBILE_375(375, 812);

    private final int widthPx;
    private final int heightPx;

    ViewportType(int widthPx, int heightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public int getHeightPx() {
        return heightPx;
    }
}
