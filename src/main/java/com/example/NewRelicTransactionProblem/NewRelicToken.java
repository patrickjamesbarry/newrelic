package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.Token;
import com.newrelic.api.agent.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NewRelicToken implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(NewRelicToken.class);

    private final Token token;

    public NewRelicToken(Transaction transaction) {
        log.info("Attempting to use token {}", getDebuggingCallerInfo(List.of(this.getClass().getName(), ThreadContext.class.getName(), RequestContext.class.getName()), List.of()));
        token = transaction.getToken(); //Beware, Even though this is a get(), it creates a new token!
        linkThreadToNewRelicTransaction();
    }

    public boolean isActive() {
        return isRunningWithNewRelicAgent(token) && token.isActive();
    }

    @Override
    public void close() {
        token.expire();
    }

    private void linkThreadToNewRelicTransaction() {
        if (isRunningWithNewRelicAgent(token)) {
            if (token.isActive()) {
                boolean isLinkSuccessful = token.link();
                if (log.isTraceEnabled()) {
                    String msg = isLinkSuccessful ? "successful" : "not needed (on same thread)";
                    if (!token.isActive()) {
                        msg = "ignored since token was EXPIRED";
                    }

                    log.trace("Newrelic token linkage was {} {}", msg, getDebuggingCallerInfo(List.of(this.getClass()
                            .getName()), List.of()));
                }
            } else {
                log.warn("New Relic Transaction/Token appears to have ended prematurely {}", getDebuggingCallerInfo(List.of(this.getClass().getName(), ThreadContext.class.getName(), RequestContext.class.getName()), List.of()));
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

    public static String getDebuggingCallerInfo(List<String> ignoreFullyQualifiedClassNames, List<String> methodNamesToIgnore) {
        var trace = getCallersTrace(Thread.currentThread()
                .getStackTrace(), ignoreFullyQualifiedClassNames, methodNamesToIgnore);
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
