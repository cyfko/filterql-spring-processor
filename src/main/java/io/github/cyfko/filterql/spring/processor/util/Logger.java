package io.github.cyfko.filterql.spring.processor.util;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Simple logger wrapper for the annotation processor Messager.
 * <p>
 * Standardizes log messages with a prefix `[ExposureProcessor]` for easier
 * debugging.
 * </p>
 */
public class Logger {
    private final Messager messager;

    /**
     * Constructs a Logger with the processing environment.
     *
     * @param processingEnv the processing environment
     */
    public Logger(ProcessingEnvironment processingEnv) {
        this.messager = processingEnv.getMessager();
    }

    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    public void log(String message) {
        messager.printMessage(
                Diagnostic.Kind.NOTE,
                "[ExposureProcessor] " + message);
    }

    /**
     * Logs an error message bound to a specific element.
     * This will cause the compilation to fail.
     *
     * @param message the error message
     * @param element the element associated with the error
     */
    public void error(String message, Element element) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                "[ExposureProcessor] " + message,
                element);
    }

    /**
     * Logs a warning message bound to a specific element.
     *
     * @param message the warning message
     * @param element the element associated with the warning
     */
    public void warning(String message, Element element) {
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                "[ExposureProcessor] " + message,
                element);
    }

    /**
     * Logs a note message (same as log).
     *
     * @param message the message to log
     */
    public void note(String message) {
        messager.printMessage(
                Diagnostic.Kind.NOTE,
                "[ExposureProcessor] " + message);
    }
}
