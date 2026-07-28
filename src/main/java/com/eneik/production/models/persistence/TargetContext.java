package com.eneik.production.models.persistence;

/**
 * First-class target context defining whether a wishlist/task targets the client product codebase
 * or the underlying orchestrator system engine.
 */
public enum TargetContext {
    PRODUCT_CODEBASE,
    ORCHESTRATOR_SYSTEM
}
