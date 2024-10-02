package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Token;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.Closeable;
import java.util.List;
import java.util.StringJoiner;

class ThreadContext extends HttpClientContext implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ThreadContext.class);
    private final Token newRelicToken;
    private boolean isClosed = false;
    private static final List<String> CLASSES_TO_IGNORE = List.of(ThreadContext.class.getName(), RequestContext.class.getName());
    private static final List<String> METHODS_TO_IGNORE = List.of();

    ThreadContext() {
        newRelicToken = NewRelic.getAgent().getTransaction().getToken(); //Beware, Even though this is a get(), it creates a new token!
        if (isRunningWithNewRelicAgent(newRelicToken) && !newRelicToken.isActive()) {
            log.error("Was not able to initialize NewRelic Token");
        }
    }

    public Token getCurrentNewRelicToken(){
        return newRelicToken;
    }

    public void restoreThreadContext() {
        linkThreadToNewRelicTransaction();
    }

    void linkThreadToNewRelicTransaction() {
        if (isRunningWithNewRelicAgent(newRelicToken)) {
            if (newRelicToken.isActive()) {
                boolean isLinkSuccessful = newRelicToken.link();
                if (log.isTraceEnabled()) {
                    String msg = isLinkSuccessful ? "successful" : "not needed (on same thread)";
                    if (!newRelicToken.isActive()) {
                        msg = "ignored since token was EXPIRED";
                    }

                    log.trace("Newrelic token linkage was {} {}", msg, getDebuggingCallerInfo(CLASSES_TO_IGNORE, METHODS_TO_IGNORE));
                }
            } else {
                log.warn("New Relic Transaction/Token appears to have ended prematurely {}", getDebuggingCallerInfo(CLASSES_TO_IGNORE, METHODS_TO_IGNORE));
            }
        }
    }

    /**
     * From NewRelic docs
     * <p>
     * By default, a transaction can create a maximum of 3000 tokens and each token has a default timeout of 180s.
     * You can change the former limit with the token_limit config option and the latter with the token_timeout config option.
     * Traces for transactions that exceed the token_limit will contain a token_clamp attribute. Increasing either
     * config option may increase agent memory usage.
     * </p>
     */
    private boolean isRunningWithNewRelicAgent(Token newRelicToken) {
        return !newRelicToken.getClass().getName().contains(".NoOp"); //note: getSimpleName is blank! do not use
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("newRelicToken=" + newRelicToken.isActive())
                .toString();
    }

    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Does not wipeout internal state of this class, rather releases external resources
     */
    public void close() {
        if (isClosed) {
            log.error("Context already closed");
        } else {
            newRelicToken.expire();
            MDC.clear();
            isClosed = true;
        }
    }

    public static String getDebuggingCallerInfo(List<String> ignoreFullyQualifiedClassNames, List<String> methodNamesToIgnore) {
        var trace = getCallersTrace(Thread.currentThread().getStackTrace(), ignoreFullyQualifiedClassNames, methodNamesToIgnore);
        String className = trace.getClassName().substring(trace.getClassName().lastIndexOf(".") + 1);
        return String.format("@%s.%s:%d", className, trace.getMethodName(), trace.getLineNumber());
    }

    private static StackTraceElement getCallersTrace(StackTraceElement[] elements, List<String> ignoreFullyQualifiedClassNames, List<String> methodNamesToIgnore) {
        try {
            int index = elements.length > 1 ? 2 : elements.length - 1; //Skip first trace

            for (; index < elements.length; index++) { //Skip over zero
                String callingClass = elements[index].getClassName();
                String methodName = elements[index].getMethodName();

                if (!ignoreFullyQualifiedClassNames.contains(callingClass) && !methodNamesToIgnore.contains(methodName)) {
                    break;
                }
            }

            return elements[index];
        } catch (Exception ex) {
            log.error("getCallersTrace threw exception " + ex.getMessage(), ex);
        }
        return elements.length > 0 ? elements[0] : new StackTraceElement("NoOp", "NoMethod", "NoFile", 0); //Return something that will not break the caller.
    }
}
