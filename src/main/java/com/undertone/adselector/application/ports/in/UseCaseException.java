package com.undertone.adselector.application.ports.in;

import com.undertone.adselector.model.exceptions.ApplicationException;

public class UseCaseException extends ApplicationException {
    public UseCaseException(String message) { super(message); }
    public UseCaseException(String message, Throwable cause) { super(message, cause); }
    public UseCaseException(Throwable cause) { super(cause); }

    public static class RejectionException extends UseCaseException {
        public RejectionException(String message) { super(message); }
        public RejectionException(String message, Throwable cause) { super(message, cause); }
        public RejectionException(Throwable cause) { super(cause); }
    }

    public static class AbortedException extends UseCaseException {
        public AbortedException(String message) { super(message); }
        public AbortedException(String message, Throwable cause) { super(message, cause); }

        public static class TypeConversionException extends ApplicationException {
            public TypeConversionException(String message, Throwable cause) { super(message, cause); }
            public TypeConversionException(String message) { super(message); }

        }
    }

}
