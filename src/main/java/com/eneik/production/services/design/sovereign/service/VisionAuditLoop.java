package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import com.eneik.production.services.design.sovereign.domain.VisionAuditReport;

import java.util.List;

/**
 * Service performing visual quality auditing against deontic gates (Leica/GPS noise, 375px mobile scroll)
 * and generating scores and correction directives.
 */
public interface VisionAuditLoop {

    /**
     * Audits a synthesized DOM document and its viewport captures.
     */
    VisionAuditReport auditDocument(SynthesizedDomDocument document, List<ViewportCapture> captures);
}
