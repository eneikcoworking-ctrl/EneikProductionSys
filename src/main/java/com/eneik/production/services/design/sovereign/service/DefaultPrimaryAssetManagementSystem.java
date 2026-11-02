package com.eneik.production.services.design.sovereign.service;

import com.eneik.production.services.design.sovereign.domain.SynthesizedDomDocument;
import com.eneik.production.services.design.sovereign.domain.ViewportCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of {@link PrimaryAssetManagementSystem} logging and registering submitted assets.
 */
@Component
public class DefaultPrimaryAssetManagementSystem implements PrimaryAssetManagementSystem {

    private static final Logger log = LoggerFactory.getLogger(DefaultPrimaryAssetManagementSystem.class);

    @Override
    public void submitAssets(String projectContextKey, SynthesizedDomDocument document, List<ViewportCapture> captures) {
        log.info("PrimaryAssetManagementSystem: submitted assets for context='{}', domFingerprint={}, capturesCount={}",
                projectContextKey,
                document != null ? document.domFingerprint() : "null",
                captures != null ? captures.size() : 0);
    }
}
