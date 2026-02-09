package io.regulyn.identity.service;

import io.regulyn.identity.constants.TenantStatuses;

public final class TenantLifecyclePolicy {

    private TenantLifecyclePolicy() {
    }

    public static boolean canActivate(String status) {
        return TenantStatuses.DRAFT.equals(status);
    }

    public static boolean canSuspend(String status) {
        return TenantStatuses.ACTIVE.equals(status);
    }

    public static boolean canResume(String status) {
        return TenantStatuses.SUSPENDED.equals(status);
    }

    public static boolean canDeleteRequest(String status) {
        return TenantStatuses.DRAFT.equals(status)
                || TenantStatuses.ACTIVE.equals(status)
                || TenantStatuses.SUSPENDED.equals(status);
    }

    public static boolean canHardDelete(String status) {
        return !TenantStatuses.DELETED.equals(status);
    }
}
