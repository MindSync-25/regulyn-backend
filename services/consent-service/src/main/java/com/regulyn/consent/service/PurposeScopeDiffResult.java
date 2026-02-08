package com.regulyn.consent.service;

import java.util.List;

record PurposeScopeDiffResult(
        boolean widened,
        boolean narrowed,
        boolean changed,
        List<String> reasons
) {
}
