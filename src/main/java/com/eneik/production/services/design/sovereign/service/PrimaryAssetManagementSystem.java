package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;

import java.util.List;

/**
 * Interface representing the primary asset management system.
 * Handles the submission of generated design assets.
 */
public interface PrimaryAssetManagementSystem {

    /**
     * Submits the final synthesized DOM document and media assets to the primary asset management system.
     *
     * @param projectContextKey the project context key
     * @param document the final synthesized DOM document
     * @param captures the final viewport captures
     */
    void submitAssets(String projectContextKey, SynthesizedDomDocument document, List<ViewportCapture> captures);
}
