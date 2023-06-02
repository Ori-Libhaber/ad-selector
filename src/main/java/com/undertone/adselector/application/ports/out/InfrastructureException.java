package com.undertone.adselector.application.ports.out;

import com.undertone.adselector.model.exceptions.ApplicationException;

public class InfrastructureException extends ApplicationException {
    public InfrastructureException(String message) { super(message); }
    public InfrastructureException(String message, Throwable cause) { super(message, cause); }
    public InfrastructureException(Throwable cause) { super(cause); }

    public static class StoreException extends InfrastructureException {

        public StoreException(String message) { super(message); }
        public StoreException(String message, Throwable cause) { super(message, cause); }
        public StoreException(Throwable cause) { super(cause); }

        public static class InitializationException extends StoreException {
            public InitializationException(String message) { super(message); }
            public InitializationException(String message, Throwable cause) { super(message, cause); }
        }

        public static class OperationFailedException extends StoreException {
            public OperationFailedException(String message) { super(message); }
            public OperationFailedException(String message, Throwable cause) { super(message, cause); }
        }
    }
}
