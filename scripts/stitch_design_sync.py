#!/usr/bin/env python3
"""
Sovereign Stitch Design System Sync & Token Bridge.
Maintains connection between EneikProductionSys and Dmitry Efremov Platform.
"""

import os
import json
import urllib.request

STITCH_KEY = os.environ.get("STITCH_API_KEY", "")
MCP_URL = "https://stitch.googleapis.com/mcp"
PROJECT_ID = "11323511138897290737"
DESIGN_SYSTEM_ASSET = "assets/14007979163025038924"

FACTORY_DATA_PATH = "/opt/EneikProductionSys/data/design_system_sovereign.json"
SITE_TOKENS_PATH = "/opt/dmitryefremov-site/frontend/src/lib/design_tokens.json"

DESIGN_TOKENS = {
    "stitchProjectId": PROJECT_ID,
    "stitchDesignSystemAsset": DESIGN_SYSTEM_ASSET,
    "systemName": "Dmitry Efremov Sovereign Epistemic",
    "neurophysiology": {
        "contrastRatioTarget": "9.5:1",
        "melanopicProtection": "Attenuated short-wavelength (460-480nm) peaks",
        "saccadicFriction": "Minimal peripheral capture via subpixel dividers"
    },
    "typography": {
        "fontFamilyHeadings": "'Geist', 'Inter', -apple-system, sans-serif",
        "fontFamilyBody": "'Inter', -apple-system, sans-serif",
        "fontFamilyMono": "'JetBrains Mono', monospace",
        "trackingHeadings": "-0.02em",
        "lineHeightBody": "1.65"
    },
    "colors": {
        "dark": {
            "bg": "#0C0E12",
            "surface": "#13161D",
            "surfaceElevated": "#181C26",
            "border": "rgba(235, 240, 255, 0.08)",
            "borderFocus": "rgba(212, 175, 55, 0.5)",
            "textPrimary": "#ECEFF5",
            "textSecondary": "#C8CDD8",
            "textMuted": "#767E90",
            "accent": "#D4AF37",
            "accentSoft": "rgba(212, 175, 55, 0.15)",
            "statusAgreed": "#38A169",
            "statusDiscussion": "#D69E2E",
            "statusNew": "#D4AF37",
            "statusArchived": "#4A5568"
        },
        "light": {
            "bg": "#F7F8FA",
            "surface": "#FFFFFF",
            "surfaceElevated": "#F0F2F5",
            "border": "rgba(0, 0, 0, 0.08)",
            "borderFocus": "rgba(180, 140, 30, 0.5)",
            "textPrimary": "#1A1D24",
            "textSecondary": "#4A5568",
            "textMuted": "#8A94A6",
            "accent": "#B78E20",
            "accentSoft": "rgba(183, 142, 32, 0.12)",
            "statusAgreed": "#2F855A",
            "statusDiscussion": "#B7791F",
            "statusNew": "#B78E20",
            "statusArchived": "#718096"
        }
    }
}

def main():
    print(f"[STITCH_BRIDGE] Linking Factory Stitch Asset {DESIGN_SYSTEM_ASSET}...")
    
    os.makedirs(os.path.dirname(FACTORY_DATA_PATH), exist_ok=True)
    with open(FACTORY_DATA_PATH, "w", encoding="utf-8") as f:
        json.dump(DESIGN_TOKENS, f, ensure_ascii=False, indent=2)
    print(f"[STITCH_BRIDGE] Factory config saved: {FACTORY_DATA_PATH}")

    os.makedirs(os.path.dirname(SITE_TOKENS_PATH), exist_ok=True)
    with open(SITE_TOKENS_PATH, "w", encoding="utf-8") as f:
        json.dump(DESIGN_TOKENS, f, ensure_ascii=False, indent=2)
    print(f"[STITCH_BRIDGE] Site design tokens synced: {SITE_TOKENS_PATH}")

if __name__ == "__main__":
    main()
