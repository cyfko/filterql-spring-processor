package io.github.cyfko.filterql.spring.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ExposureAnnotationProcessorIntegrationTest {

    @Test
    void testBothProjectionsGenerated() throws IOException {
        getJavaFileObjects();

        Compilation compilation = Compiler.javac()
                .withProcessors(new ExposureAnnotationProcessor())
                .compile(getJavaFileObjects());

        assertThat(compilation).succeeded();

        String generatedCode = getGeneratedControllerCode(compilation);

        // Verify both DTOs are in the generated registry
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.tenantIsolation(req);"));
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.softDelete(req);"));
        assertTrue(generatedCode.contains("req = instanceResolver.resolve(io.github.cyfko.example.UserPipes.class, null).activeUsersOnly(req);"));
        assertTrue(generatedCode.contains("return searchService.search(io.github.cyfko.example.PersonDTO_.class, req);"));
        assertTrue(generatedCode.contains("public PaginatedData<Map<String, Object>> searchPersonDTO"));
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.softDelete(req);"));
        assertTrue(generatedCode.contains("public PaginatedData<io.github.cyfko.example.AddressDTO> searchAddressDTO"));
        assertTrue(generatedCode.contains("return instanceResolver.resolve(io.github.cyfko.example.AdminRightResolver.class, null).handleAddressSearch(req);"));
        assertTrue(generatedCode.contains("public boolean searchGeometryDTO"));
        assertTrue(generatedCode.contains("return io.github.cyfko.example.GeometryDTO.handleGeometrySearch(req);"));
        assertTrue(generatedCode.contains("public List<io.github.cyfko.example.SegmentDTO> searchSegmentDTO"));
        assertTrue(generatedCode.contains("return searchService.search(io.github.cyfko.example.SegmentDTO_.class, req);"));
    }

    private static JavaFileObject[] getJavaFileObjects() {
        return new  JavaFileObject[] {
                JavaFileObjects.forResource("testdata/Person.java"),
                JavaFileObjects.forResource("testdata/PersonDTO.java"),
                JavaFileObjects.forResource("testdata/Address.java"),
                JavaFileObjects.forResource("testdata/AddressDTO.java"),
                JavaFileObjects.forResource("testdata/Geometry.java"),
                JavaFileObjects.forResource("testdata/GeometryDTO.java"),
                JavaFileObjects.forResource("testdata/Segment.java"),
                JavaFileObjects.forResource("testdata/SegmentDTO.java"),
                JavaFileObjects.forResource("testdata/UserTenancyResolvers.java"),
                JavaFileObjects.forResource("testdata/AdminRightResolver.java"),
                JavaFileObjects.forResource("testdata/BasePipes.java"),
                JavaFileObjects.forResource("testdata/UserPipes.java")
        };
    }

    private String getGeneratedControllerCode(Compilation compilation) throws IOException {
        return compilation
                .generatedSourceFile("io.github.cyfko.filterql.spring.controller.FilterQlController")
                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                .getCharContent(true)
                .toString();
    }
}
