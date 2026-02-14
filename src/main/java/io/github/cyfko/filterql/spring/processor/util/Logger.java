package io.github.cyfko.filterql.spring.processor.util;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class Logger {
    private final Messager messager;
    
    public Logger(ProcessingEnvironment processingEnv) {
        this.messager = processingEnv.getMessager();
    }
    
    public void log(String message) {
        messager.printMessage(
                Diagnostic.Kind.NOTE,
                "[ExposureProcessor] " + message);
    }

    public void error(String message, Element element) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                "[ExposureProcessor] " + message,
                element);
    }

    public void warning(String message, Element element) {
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                "[ExposureProcessor] " + message,
                element);
    }

    public void note(String message) {
        messager.printMessage(
                Diagnostic.Kind.NOTE,
                "[ExposureProcessor] " + message);
    }
}
