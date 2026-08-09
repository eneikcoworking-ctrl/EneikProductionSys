package com.eneik.production.services.lever;

/**
 * The 5-stage promotion ladder OperationalTruthService.promotionPolicy() already documents as vocabulary
 * (observe_only -> warn_only -> soft_gate -> hard_gate -> auto_remediate) - this is the first thing that
 * actually walks a decision lever along it, based on accumulated real evidence (LeverPromotionService),
 * not a deploy or a timer. Wire values match OperationalTruthService's strings exactly - one canonical
 * vocabulary, not a second competing one.
 */
public enum LeverStage {
    OBSERVE_ONLY("observe_only"),
    WARN_ONLY("warn_only"),
    SOFT_GATE("soft_gate"),
    HARD_GATE("hard_gate"),
    AUTO_REMEDIATE("auto_remediate");

    private final String wireValue;

    LeverStage(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean atLeast(LeverStage other) {
        return this.ordinal() >= other.ordinal();
    }

    public static LeverStage fromWireValue(String value) {
        for (LeverStage stage : values()) {
            if (stage.wireValue.equals(value)) {
                return stage;
            }
        }
        return OBSERVE_ONLY;
    }

    public LeverStage next() {
        int idx = this.ordinal();
        return idx + 1 < values().length ? values()[idx + 1] : this;
    }

    public LeverStage previous() {
        int idx = this.ordinal();
        return idx - 1 >= 0 ? values()[idx - 1] : this;
    }
}
