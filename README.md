# FilterQL Spring Processor

[![Maven Central](https://img.shields.io/maven-central/v/io.github.cyfko/filterql-spring-processor)](https://search.maven.org/artifact/io.github.cyfko/filterql-spring-processor)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen?logo=spring-boot)](https://spring.io/projects/spring-boot)

**FilterQL Spring Processor** is a Java annotation processor that generates PropertyRef enums, FilterContext configurations, and REST controllers for Spring Boot applications using FilterQL.

## 🎯 Goals

This processor provides:

- **Automatic PropertyRef enum generation**: Type-safe filter property enumerations
- **Spring configuration generation**: `@Configuration` classes with `JpaFilterContext` beans
- **REST controller generation**: Search endpoints with `@Exposure` annotation
- **Compile-time validation**: Field existence and operator compatibility checks
- **Template-based code generation**: Customizable output via templates

## 📋 Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Spring Boot 3.3.5+**
- **filterql-spring-api** (required dependency)
- **projection-metamodel-processor** (external dependency)

## 🚀 Installation

### Maven

Add the processor as a `provided` dependency:

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-processor</artifactId>
    <version>4.0.0</version>
    <scope>provided</scope>
</dependency>
```

The annotation processor will be automatically detected and executed during compilation thanks to `auto-service`.

> **Note:** This module requires `filterql-spring-api` as a dependency. Make sure you have it in your runtime dependencies.

## 🏗️ Architecture

### Processing Flow

```
Compile Time:
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  @Projection + @Exposure Annotations                         │
│              ↓                                               │
│  ExposureAnnotationProcessor                                 │
│              ↓                                               │
│  ┌────────────────┬──────────────────┬────────────────────┐ │
│  │                │                  │                    │ │
│  ▼                ▼                  ▼                    │ │
│  FieldAnalyzer   PropertyRefEnum    FilterContext         │ │
│                  Generator          Generator             │ │
│                  │                  │                     │ │
│                  ▼                  ▼                     │ │
│  ┌──────────────────────────────────────────────┐        │ │
│  │  Generated Files:                            │        │ │
│  │  • UserDTO_.java (PropertyRef enum)          │        │ │
│  │  • FilterQlContextConfig.java (Spring @Bean) │        │ │
│  │  • FilterQlController.java (REST endpoints)  │←───────┘ │
│  └──────────────────────────────────────────────┘          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Processor Lifecycle

1. **Discovery**: Finds all classes annotated with `@Projection` (from external library)
2. **Analysis**: Extracts field metadata using `FieldAnalyzer`
3. **Generation**: Produces three types of artifacts:
    - PropertyRef enum (always)
    - FilterContext configuration (always)
    - REST controller (only if `@Exposure` present)
4. **Validation**: Ensures field paths exist and operators are compatible

## 🔍 Detailed Components

### 1. ExposureAnnotationProcessor

Main annotation processor that orchestrates code generation.

**Supported Annotations:**
- `io.github.cyfko.projection.Projection` (external - from projection-spec)
- `io.github.cyfko.filterql.spring.annotation.Exposure` (from filterql-spring-api)

**Processing Steps:**

```java
@SupportedAnnotationTypes({
    "io.github.cyfko.projection.Projection"
})
@AutoService(Processor.class)
public class ExposureAnnotationProcessor extends AbstractProcessor {
    
    @Override
    public boolean process(
        Set<? extends TypeElement> annotations, 
        RoundEnvironment roundEnv
    ) {
        // 1. Find @Projection classes
        Set<? extends Element> projections = 
            roundEnv.getElementsAnnotatedWith(Projection.class);
        
        for (Element element : projections) {
            // 2. Analyze fields
            List<FieldMetadata> fields = fieldAnalyzer.analyze(element);
            
            // 3. Generate PropertyRef enum
            propertyRefGenerator.generate(element, fields);
            
            // 4. Generate FilterContext configuration
            filterContextGenerator.generate(element, fields);
            
            // 5. Generate REST controller (if @Exposure present)
            if (element.getAnnotation(Exposure.class) != null) {
                controllerGenerator.generate(element, fields);
            }
        }
        
        return false; // Allow other processors to process @Projection
    }
}
```

### 2. Code Generators

#### PropertyRefEnumGenerator

Generates type-safe PropertyReference enums from DTO projections.

**Template:** `property-ref-enum.java.tpl`

**Input:**
```java
@Projection(from = User.class)
@Exposure("users")
public interface UserDTO {
    
    @Projected
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES})
    String getUsername();
    
    @Projected
    @ExposedAs(value = "AGE", operators = {Op.GT, Op.LT})
    Integer getAge();
}
```

**Generated Output:**
```java
package com.example.dto;

import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.jpametamodel.ProjectionRegistry;

public enum UserDTO_ implements PropertyReference {
    USERNAME,
    AGE;
    
    @Override
    public Class<?> getType() {
        var pm = ProjectionRegistry.getMetadataFor(UserDTO.class);
        return switch(this) {
            case USERNAME -> pm.getDirectMapping("username", true)
                .orElseThrow().dtoFieldType();
            case AGE -> pm.getDirectMapping("age", true)
                .orElseThrow().dtoFieldType();
        };
    }
    
    @Override
    public Set<Op> getSupportedOperators() {
        return switch(this) {
            case USERNAME -> Set.of(Op.EQ, Op.MATCHES);
            case AGE -> Set.of(Op.GT, Op.LT);
        };
    }
    
    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

**Features:**
- Enum constants from `@ExposedAs` values
- Dynamic type resolution via `ProjectionRegistry`
- Operator validation based on `@ExposedAs.operators()`
- Maintains link to source entity

#### FilterContextGenerator

Generates Spring `@Configuration` class with `JpaFilterContext` beans.

**Templates:**
- `filter-context-instance.java.tpl` (bean method)
- `filter-context-config.java.tpl` (configuration class wrapper)

**Generated Output:**
```java
package io.github.cyfko.filterql.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.core.resolver.InstanceResolver;

@Configuration
public class FilterQlContextConfig {
    
    @Bean
    public JpaFilterContext<UserDTO_> userDTOContext(
        InstanceResolver instanceResolver
    ) {
        return new JpaFilterContext<>(
            UserDTO_.class,
            (ref) -> switch (ref) {
                case USERNAME -> "username";
                case AGE -> "age";
                // Virtual fields return PredicateResolverMapping
                case FULL_NAME -> UserDTO.fullNameMatches();
            }
        );
    }
}
```

**Features:**
- One bean method per projection class
- Automatic path mapping via switch expression
- Support for virtual fields (custom predicates)
- IoC-ready with `InstanceResolver` injection

#### FilterControllerGenerator

Generates REST controller with search endpoints (only if `@Exposure` present).

**Templates:**
- `search-controller.java.tpl` (controller class)
- `search-endpoint.java.tpl` (endpoint method)

**Generated Output:**
```java
package io.github.cyfko.filterql.spring.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;

@RestController
@RequestMapping("/api/v1")
public class FilterQlController {
    
    private final FilterQlService filterQlService;
    
    public FilterQlController(FilterQlService filterQlService) {
        this.filterQlService = filterQlService;
    }
    
    @PostMapping("/users/search")
    public ResponseEntity<PaginatedData<Map<String, Object>>> searchUserDTO(
        @RequestBody @Validated FilterRequest<UserDTO_> request
    ) {
        PaginatedData<Map<String, Object>> result = 
            filterQlService.search(UserDTO_.class, request);
        return ResponseEntity.ok(result);
    }
}
```

**Features:**
- REST endpoint per `@Exposure` annotation
- Automatic path construction (`{basePath}/{resource}/search`)
- Validation via `@Validated`
- Returns `PaginatedData<Map<String, Object>>`
- Optional custom annotations via `@Exposure.annotationsFrom()`

### 3. Field Analysis

#### FieldAnalyzer

Extracts metadata from DTO fields for code generation.

**Analyzed Elements:**
- `@Projected` fields (from external projection-spec)
- `@ExposedAs` annotations (from filterql-spring-api)
- Field types and generic parameters
- JPA paths via `ProjectionRegistry`

**Extracted Metadata:**
```java
public record FieldMetadata(
    String fieldName,          // Java field name
    String exposedName,        // Enum constant name (@ExposedAs value)
    String entityPath,         // JPA path (e.g., "address.city")
    Class<?> fieldType,        // Java type
    Set<Op> operators,         // Supported operators
    boolean virtual,           // Whether field is computed/virtual
    String predicateMethod     // Custom predicate method (if virtual)
) {}
```

**Example:**
```java
@Projected
@ExposedAs(value = "CITY", operators = {Op.EQ, Op.IN})
private String city;

// Extracted metadata:
new FieldMetadata(
    "city",                    // fieldName
    "CITY",                    // exposedName
    "address.city",            // entityPath (from ProjectionRegistry)
    String.class,              // fieldType
    Set.of(Op.EQ, Op.IN),     // operators
    false,                     // virtual
    null                       // predicateMethod
)
```

## 📁 Generated Files

All files are generated in `target/generated-sources/annotations/`:

```
target/generated-sources/annotations/
├── com/example/dto/
│   └── UserDTO_.java                      # PropertyRef enum
├── io/github/cyfko/filterql/spring/
│   ├── config/
│   │   └── FilterQlContextConfig.java     # Spring configuration
│   └── controller/
│       └── FilterQlController.java        # REST controller (if @Exposure)
```

## 🎨 Template System

### Available Templates

Located in `src/main/resources/templates/`:

1. **property-ref-enum.java.tpl**
    - Generates PropertyReference enum
    - Variables: `${packageName}`, `${enumName}`, `${constants}`, etc.

2. **filter-context-instance.java.tpl**
    - Generates individual bean method
    - Variables: `${beanName}`, `${enumName}`, `${switchCases}`

3. **filter-context-config.java.tpl**
    - Wraps bean methods in `@Configuration` class
    - Variables: `${packageName}`, `${beanMethods}`

4. **search-controller.java.tpl**
    - Generates controller class
    - Variables: `${packageName}`, `${endpoints}`

5. **search-endpoint.java.tpl**
    - Generates endpoint method
    - Variables: `${basePath}`, `${resourceName}`, `${enumName}`

### Template Variables

**Common Variables:**
- `${packageName}` - Target package name
- `${className}` - Generated class name
- `${imports}` - Import statements

**Enum-Specific:**
- `${enumName}` - Enum name (e.g., `UserDTO_`)
- `${constants}` - Enum constant declarations
- `${enumToFieldTypeSwitch}` - Type resolution switch
- `${enumToOperatorsSwitch}` - Operator mapping switch
- `${entityClass}` - Source entity class

**Context-Specific:**
- `${beanName}` - Bean method name (e.g., `userDTOContext`)
- `${switchCases}` - Path mapping switch cases
- `${hasVirtualFields}` - Boolean flag for InstanceResolver parameter

**Controller-Specific:**
- `${basePath}` - REST base path (e.g., `/api/v1`)
- `${resourceName}` - Resource name (e.g., `users`)
- `${methodName}` - Method name (e.g., `searchUserDTO`)
- `${annotationDecorators}` - Additional annotations (@PreAuthorize, etc.)

## ⚙️ Configuration

### Disable Processor

If you need to disable the processor temporarily:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <proc>none</proc>
    </configuration>
</plugin>
```

### Debug Mode

Enable verbose output:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>-Averbose=true</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### Custom Package

Currently, generated packages are fixed:
- Enums: Same package as projection class
- Config: `io.github.cyfko.filterql.spring.config`
- Controller: `io.github.cyfko.filterql.spring.controller`

Future versions may support customization via processor options.

## 🔧 Usage Example

### Complete Workflow

**Step 1: Define Projection**

```java
package com.example.dto;

import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.filterql.spring.annotation.Exposure;
import io.github.cyfko.filterql.spring.annotation.ExposedAs;
import io.github.cyfko.filterql.core.api.Op;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public interface UserDTO {
    
    @Projected
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getUsername();
    
    @Projected
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    String getEmail();
    
    @Projected
    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE})
    Integer getAge();
}
```

**Step 2: Compile**

```bash
mvn clean compile
```

**Step 3: Use Generated Code**

```java
// Use generated enum
FilterRequest<UserDTO_> request = FilterRequest.<UserDTO_>builder()
    .filter("f1", UserDTO_.USERNAME, Op.MATCHES, "john%")
    .filter("f2", UserDTO_.AGE, Op.GT, 25)
    .combineWith("f1 & f2")
    .pagination(new Pagination(0, 20))
    .build();

// Use generated endpoint
curl -X POST http://localhost:8080/api/v1/users/search \
  -H "Content-Type: application/json" \
  -d '{"filters": {...}, "pagination": {...}}'
```

## ⚠️ Limitations

### 1. Fixed Package Structure

**Constraint:** Generated classes use predefined package locations.

**Impact:** Cannot customize output packages without modifying templates.

### 2. Single Configuration Class

**Constraint:** All `JpaFilterContext` beans are generated in a single `FilterQlContextConfig` class.

**Impact:** May cause merge conflicts in multi-module projects with many projections.

### 3. Template Bundling

**Constraint:** Templates are bundled in JAR (`src/main/resources/templates/`).

**Impact:** Cannot override templates without recompiling the processor.

### 4. Naming Convention

**Constraint:** PropertyRef enums are named `{ClassName}_` (e.g., `UserDTO_`).

**Impact:** Cannot customize enum naming pattern.

### 5. REST Endpoint Pattern

**Constraint:** Generated endpoints follow pattern `{basePath}/{resource}/search`.

**Impact:** Cannot generate multiple endpoints or custom paths per projection.

## 🔧 Troubleshooting

### Processor Not Running

**Symptom:** No files generated in `target/generated-sources/annotations/`

**Causes:**
1. Missing `@Projection` annotation on DTO
2. Processor disabled (`<proc>none</proc>`)
3. Missing `auto-service` dependency

**Solution:**
```bash
# Check processor is registered
mvn clean compile -X | grep "ExposureAnnotationProcessor"

# Verify annotation is present
grep -r "@Projection" src/main/java/
```

### Compilation Errors in Generated Code

**Symptom:** Errors like "cannot find symbol UserDTO_"

**Causes:**
1. Annotation processing failed silently
2. Generated files not in classpath
3. IDE not recognizing generated sources

**Solution:**
```bash
# Force regeneration
mvn clean compile

# For IntelliJ IDEA:
File → Project Structure → Modules → Mark 'target/generated-sources/annotations' as Sources

# For Eclipse:
Project Properties → Java Build Path → Add 'target/generated-sources/annotations'
```

### Missing FilterQlContextConfig

**Symptom:** `NoSuchBeanDefinitionException: No qualifying bean of type 'JpaFilterContext'`

**Causes:**
1. Generated config class not in component scan
2. Spring Boot auto-configuration not loaded
3. Missing `@SpringBootApplication` or `@ComponentScan`

**Solution:**
```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example",
    "io.github.cyfko.filterql.spring"  // Include generated packages
})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Controller Endpoint Not Found

**Symptom:** 404 error when calling `/api/v1/users/search`

**Causes:**
1. `@Exposure` annotation missing
2. Controller not in component scan
3. Wrong base path configuration

**Solution:**
```bash
# Check if controller was generated
ls target/generated-sources/annotations/io/github/cyfko/filterql/spring/controller/

# Verify @Exposure annotation
@Exposure(value = "users", basePath = "/api/v1")

# Check Spring logs
INFO: Mapped "{[/api/v1/users/search],methods=[POST]}"
```

### Template Resolution Errors

**Symptom:** `FileNotFoundException: property-ref-enum.java.tpl`

**Causes:**
1. Templates not in classpath
2. Processor JAR corrupted
3. Resource loading issue

**Solution:**
```bash
# Verify templates in JAR
jar tf filterql-spring-processor-4.0.0.jar | grep "\.tpl$"

# Expected output:
# templates/property-ref-enum.java.tpl
# templates/filter-context-instance.java.tpl
# ...
```

## 🤝 Contributing

### Local Development

```bash
# Clone repository
git clone https://github.com/cyfko/filterql-spring-processor.git
cd filterql-spring-processor

# Build and install
mvn clean install

# Run tests
mvn test
```

### Adding New Templates

1. Create template in `src/main/resources/templates/`
2. Update generator class to use new template
3. Add test case for generated code
4. Update documentation

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 👤 Author

**Frank KOSSI**

- Email: frank.kossi@kunrin.com
- Organization: [Kunrin SA](https://www.kunrin.com)

## 🔗 Links

- [GitHub Repository](https://github.com/cyfko/filterql)
- [Issue Tracker](https://github.com/cyfko/filterql/issues)
- [Maven Central](https://search.maven.org/artifact/io.github.cyfko/filterql-spring-processor)
- [FilterQL Spring API](https://github.com/cyfko/filter-ql/tree/main/filterql-spring)
- [FilterQL JPA Adapter](https://github.com/cyfko/filter-ql/tree/main/filterql-jpa)
- [Projection Specification](https://github.com/cyfko/projection-spec)