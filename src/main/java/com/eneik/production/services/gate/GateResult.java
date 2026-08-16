package com.eneik.production.services.gate;

import java.util.List;

public record GateResult(boolean passed, String checkName, List<String> failureReasons) {}
