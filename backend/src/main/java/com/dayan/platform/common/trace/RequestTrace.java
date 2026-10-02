package com.dayan.platform.common.trace;

import org.slf4j.MDC;

public final class RequestTrace {

    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestTrace.class.getName() + ".requestId";

    private RequestTrace() {
    }

    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }
}
