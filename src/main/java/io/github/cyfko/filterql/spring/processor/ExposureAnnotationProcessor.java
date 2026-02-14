package io.github.cyfko.filterql.spring.processor;

import com.google.auto.service.AutoService;
import io.github.cyfko.filterql.spring.processor.generator.FilterContextGenerator;
import io.github.cyfko.filterql.spring.processor.generator.FilterControllerGenerator;
import io.github.cyfko.filterql.spring.processor.generator.PropertyRefEnumGenerator;
import io.github.cyfko.filterql.spring.processor.generator.TemplateEngine;
import io.github.cyfko.filterql.spring.processor.util.ExposureUtils;
import io.github.cyfko.filterql.spring.processor.util.Logger;
import io.github.cyfko.filterql.spring.processor.util.StringUtils;
import io.github.cyfko.projection.Projection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

/**
 * Annotation processor for the {@link Projection} annotation,
 * responsible for generating FilterQL integration artifacts at compile time.
 * <p>
 * This processor analyzes entity classes annotated with {@link Projection},
 * extracts filterable field metadata,
 * and generates the following:
 * <ul>
 * <li>Type-safe property reference enums</li>
 * <li>Spring configuration classes for FilterQL integration</li>
 * <li>REST controllers for filter endpoints if also annotated with
 * {@link io.github.cyfko.filterql.spring.Exposure}</li>
 * <li>Metadata registry implementations</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 * <li>Invoked automatically during Java compilation (Maven/Gradle)</li>
 * <li>Enables zero-boilerplate integration of FilterQL in Spring Boot
 * applications</li>
 * <li>Ensures type safety and validation for all filterable entities</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 * <li>Uses {@link com.google.auto.service.AutoService} for processor
 * registration</li>
 * <li>Supports Java 17 source version</li>
 * <li>Handles errors and warnings via the annotation processing API</li>
 * <li>Extensible via injected generators and analyzers</li>
 * </ul>
 *
 * <h2>Generated Artifacts</h2>
 * <ul>
 * <li>PropertyRef enums for each entity</li>
 * <li>Spring configuration classes</li>
 * <li>REST controllers for filter endpoints</li>
 * <li>Field metadata registry implementations</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
public class ExposureAnnotationProcessor extends AbstractProcessor {
    private static String GENERATED_BASE_PACKAGE = "io.github.cyfko.filterql.spring";

    private FieldAnalyzer fieldAnalyzer;
    private PropertyRefEnumGenerator enumGenerator;
    private FilterContextGenerator configGenerator;
    private FilterControllerGenerator controllerGenerator;
    private AnnotationExtractor annotationExtractor;
    private Logger logger;

    private boolean enumGenerated;
    private Map<TypeElement, List<FieldMetadata>> pendingProjections = new LinkedHashMap<>();

    // BLOC DE DEBUG - s'exécute au chargement de la classe
    static {
        System.out.println("🚨🚨🚨 ExposureAnnotationProcessor CLASS LOADED 🚨🚨🚨");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.fieldAnalyzer = new FieldAnalyzer(processingEnv);
        TemplateEngine templateEngine = new TemplateEngine();
        this.enumGenerator = new PropertyRefEnumGenerator(templateEngine);
        this.configGenerator = new FilterContextGenerator(templateEngine);
        this.controllerGenerator = new FilterControllerGenerator(templateEngine);
        this.logger = new Logger(processingEnv);
        logger.log("ExposureAnnotationProcessor initialized");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        logger.log("=== PROCESS ROUND START ===");

        if (roundEnv.processingOver()) {
            // Dernier round - générer les fichiers globaux
            if (enumGenerated){
                generateConfigFiles();
            }
            return false;
        }

        // Phase 1 : Générer tous les enums
        if (!enumGenerated) {
            for (Element element : roundEnv.getElementsAnnotatedWith(Projection.class)) {
                if (element.getKind() == ElementKind.CLASS) {
                    generateEnumsForPropertyRef((TypeElement) element);
                }
            }
            enumGenerated = true;
        } else {
            for (var element : pendingProjections.keySet()) {
                processArtefact(element);
            }
        }

        return false; // Continue vers le prochain round
    }

    private void generateEnumsForPropertyRef(TypeElement projectionClass) {
        String packageName = getPackageName(projectionClass);
        String projectionSimpleName = projectionClass.getSimpleName().toString();
        Projection annotation = projectionClass.getAnnotation(Projection.class);

        if (annotation == null) {
            logger.log("WARNING: @Projection annotation not found on " + projectionSimpleName);
            return;
        }

        String entityClassName = extractEntityClassName(annotation);
        String enumName = StringUtils.getSimpleClassName(ExposureUtils.toEnumRef(projectionClass));

        logger.log("Package: " + packageName + ", Enum: " + enumName);

        // 2. Analyser les champs
        var fields = fieldAnalyzer.analyzeProjection(projectionClass);
        pendingProjections.put(projectionClass, fields);
        logger.log("Found " + fields.size() + " filterable fields");

        if (fields.isEmpty()) {
            logger.warning("No filterable fields found in " + projectionSimpleName, projectionClass);
            return;
        }

        // 3. Générer les enum PropertyRef
        String enumCode = enumGenerator.generate(packageName, projectionSimpleName, enumName, fields);
        try {
            writeSourceFile(packageName, enumName, enumCode);
        } catch (Exception e) {
            logger.error(e.getMessage(), projectionClass);
        }
    }

    private void processArtefact(TypeElement projectionClass) {
        // 4. Générer la configuration Spring
        try {
            String packageName = getPackageName(projectionClass);
            String projectionFqcn = projectionClass.toString();
            String entityClassFqcn = extractEntityClassName(projectionClass.getAnnotation(Projection.class));
            configGenerator.register(packageName, projectionFqcn, pendingProjections.get(projectionClass), entityClassFqcn);
        } catch (Exception e) {
            logger.error(e.getMessage(), projectionClass);
        }

        // 5. Générer le contrôleur de recherche Spring
        try {
            controllerGenerator.register(processingEnv, projectionClass);
        } catch (Exception e) {
            logger.error(e.getMessage(), projectionClass);
        }
    }

    private void generateConfigFiles() {
        try {
            String configCode = configGenerator.generate();
            writeSourceFile(GENERATED_BASE_PACKAGE + ".config", "FilterQlContextConfig", configCode);
            logger.note("Generated controller file " + GENERATED_BASE_PACKAGE + ".config.FilterQlContextConfig");

            String controllerCode = controllerGenerator.generate();
            writeSourceFile(GENERATED_BASE_PACKAGE + ".controller", "FilterQlController", controllerCode);
            logger.note("Generated controller file " + GENERATED_BASE_PACKAGE + ".controller.FilterQlController");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractEntityClassName(Projection annotation) {
        try {
            return annotation.from().getName();
        } catch (MirroredTypeException e) {
            TypeMirror typeMirror = e.getTypeMirror();
            return typeMirror.toString();
        }
    }

    private void writeSourceFile(String packageName, String className, String code)
            throws IOException {
        String qualifiedName = packageName + "." + className;
        logger.log("Writing source file: " + qualifiedName);

        JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName);
        try (Writer writer = file.openWriter()) {
            writer.write(code);
        }
        logger.log("Successfully wrote: " + qualifiedName);
    }

    private String getPackageName(TypeElement element) {
        return processingEnv.getElementUtils()
                .getPackageOf(element)
                .getQualifiedName()
                .toString();
    }
}
