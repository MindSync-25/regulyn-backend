package com.regulyn.notification.entity;

/**
 * Message categories for consent bypass rules.
 * 
 * LEGAL_MANDATORY: Legal obligations (breach notifications, DSAR responses)
 *                  May bypass consent check failure (fail-open for legal compliance)
 * MARKETING: Marketing communications (newsletters, promotions)
 *           Must have consent, fail-closed on consent check failure
 * PRODUCT: Product updates, service notifications
 *         Must have consent, fail-closed on consent check failure
 */
public enum MessageCategory {
    /**
     * Legal/regulatory mandatory notifications.
     * Examples: breach notifications, DSAR responses, legal notices
     * Consent policy: May bypass consent check failures (with audit trail)
     */
    LEGAL_MANDATORY,
    
    /**
     * Marketing communications.
     * Examples: newsletters, promotional emails, campaigns
     * Consent policy: Strict consent required, fail-closed
     */
    MARKETING,
    
    /**
     * Product/service notifications.
     * Examples: feature updates, service announcements, account notifications
     * Consent policy: Consent required, fail-closed
     */
    PRODUCT
}
