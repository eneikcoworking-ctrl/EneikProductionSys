package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.ViewportType;

import java.util.List;

/**
 * Service capturing rendered screenshot captures and scroll metrics across required viewports.
 */
public interface PlaywrightVisionHarness {

    /**
     * Captures viewports for the given DOM document.
     */
    List<ViewportCapture> captureViewports(SynthesizedDomDocument document, List<ViewportType> viewports);
}
