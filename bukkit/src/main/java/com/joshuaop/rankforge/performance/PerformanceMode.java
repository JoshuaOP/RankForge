package com.joshuaop.rankforge.performance;

/**
 * Server performance tiers used by PerformanceManager.
 * Systems check this before enabling expensive operations.
 */
public enum PerformanceMode {
    /** TPS >= 18 — full effects enabled. */
    HIGH,
    /** TPS 14–17 — reduced particles / animations. */
    MEDIUM,
    /** TPS < 14  — minimal effects, GUI animations off. */
    LOW
}
