package org.ssdd.task;

/** Internal safe code, never an exception message from SQL/native libraries. */
public final class TaskFault extends RuntimeException {
    private final String code;
    private final boolean retryable;
    public TaskFault(String code,boolean retryable){super(code);this.code=code;this.retryable=retryable;}
    public String code(){return code;}
    public boolean retryable(){return retryable;}
}
