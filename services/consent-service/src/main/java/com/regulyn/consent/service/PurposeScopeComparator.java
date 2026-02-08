package com.regulyn.consent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class PurposeScopeComparator {

    PurposeScopeDiffResult compare(PurposeScope previous, PurposeScope current) {
        List<String> reasons = new ArrayList<>();
        boolean widened = false;
        boolean narrowed = false;

        if (previous == null) {
            return new PurposeScopeDiffResult(false, false, current != null && current.hasAnyValues(), reasons);
        }

        if (current == null) {
            if (previous.hasAnyValues()) {
                reasons.add("SCOPE_UNKNOWN");
                return new PurposeScopeDiffResult(true, false, true, reasons);
            }
            return new PurposeScopeDiffResult(false, false, false, reasons);
        }

        if (previous.hasAnyValues() && !current.hasAnyValues()) {
            reasons.add("SCOPE_UNKNOWN");
            return new PurposeScopeDiffResult(true, false, true, reasons);
        }

        if (!current.isProvided() && previous.hasAnyValues()) {
            reasons.add("SCOPE_UNKNOWN");
            widened = true;
        }

        if (hasNewElements(previous.getDataFields(), current.getDataFields())) {
            reasons.add("DATA_FIELDS_EXPANDED");
            widened = true;
        } else if (hasRemovedElements(previous.getDataFields(), current.getDataFields())) {
            narrowed = true;
        }

        if (hasNewElements(previous.getDataCategories(), current.getDataCategories())) {
            reasons.add("DATA_CATEGORIES_EXPANDED");
            widened = true;
        } else if (hasRemovedElements(previous.getDataCategories(), current.getDataCategories())) {
            narrowed = true;
        }

        if (hasNewElements(previous.getRecipients(), current.getRecipients())) {
            reasons.add("RECIPIENTS_EXPANDED");
            widened = true;
        } else if (hasRemovedElements(previous.getRecipients(), current.getRecipients())) {
            narrowed = true;
        }

        String prevLegal = previous.getLegalBasis();
        String newLegal = current.getLegalBasis();
        if (newLegal != null && (prevLegal == null || !prevLegal.equals(newLegal))) {
            reasons.add("LEGAL_BASIS_CHANGED");
            widened = true;
        }

        Integer prevRetention = previous.getRetentionDays();
        Integer newRetention = current.getRetentionDays();
        if (newRetention != null && prevRetention != null && newRetention > prevRetention) {
            reasons.add("RETENTION_EXTENDED");
            widened = true;
        } else if (newRetention != null && prevRetention != null && newRetention < prevRetention) {
            narrowed = true;
        } else if (prevRetention != null && newRetention == null) {
            reasons.add("RETENTION_UNKNOWN");
            widened = true;
        }

        boolean changed = widened || narrowed || !reasons.isEmpty();
        return new PurposeScopeDiffResult(widened, narrowed, changed, reasons);
    }

    private boolean hasNewElements(Set<String> oldSet, Set<String> newSet) {
        if (newSet == null || newSet.isEmpty()) {
            return false;
        }
        if (oldSet == null || oldSet.isEmpty()) {
            return !newSet.isEmpty();
        }
        for (String value : newSet) {
            if (!oldSet.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRemovedElements(Set<String> oldSet, Set<String> newSet) {
        if (oldSet == null || oldSet.isEmpty()) {
            return false;
        }
        if (newSet == null || newSet.isEmpty()) {
            return !oldSet.isEmpty();
        }
        for (String value : oldSet) {
            if (!newSet.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
