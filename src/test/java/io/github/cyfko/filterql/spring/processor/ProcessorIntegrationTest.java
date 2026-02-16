package io.github.cyfko.filterql.spring.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ProcessorIntegrationTest {

    @Test
    void testBothProjectionsGenerated() throws IOException {
        getJavaFileObjects();

        Compilation compilation = Compiler.javac()
                .withProcessors(new ExposureAnnotationProcessor())
                .compile(getJavaFileObjects());

        assertThat(compilation).succeeded();

        /* --------------------------------  */
        /* Verify generated controller code  */
        /* --------------------------------  */
        String generatedCode = getGeneratedControllerCode(compilation);

        // Verify both DTOs are in the generated registry
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.tenantIsolation(req);"));
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.softDelete(req);"));
        assertTrue(generatedCode.contains("req = instanceResolver.resolve(io.github.cyfko.example.UserPipes.class, null).activeUsersOnly(req);"));
        assertTrue(generatedCode.contains("return searchService.searchAs(io.github.cyfko.example.PersonDTO.class, req);"));
        assertTrue(generatedCode.contains("public PaginatedData<io.github.cyfko.example.PersonDTO> searchPersonDTO"));
        assertTrue(generatedCode.contains("req = io.github.cyfko.example.UserPipes.softDelete(req);"));
        assertTrue(generatedCode.contains("public PaginatedData<io.github.cyfko.example.AddressDTO> searchAddressDTO"));
        assertTrue(generatedCode.contains("return instanceResolver.resolve(io.github.cyfko.example.AdminRightResolver.class, null).handleAddressSearch(req);"));
        assertTrue(generatedCode.contains("public boolean searchGeometryDTO"));
        assertTrue(generatedCode.contains("return io.github.cyfko.example.GeometryDTO.handleGeometrySearch(req);"));
        assertTrue(generatedCode.contains("public List<io.github.cyfko.example.SegmentDTO> searchSegmentDTO"));
        assertTrue(generatedCode.contains("return searchService.searchAs(io.github.cyfko.example.SegmentDTO.class, req).data();"));

        /* ----------------------------  */
        /* Verify generated enums names  */
        /* ----------------------------  */
        generatedCode = getGeneratedAddressEnumCode(compilation);

        assertTrue(generatedCode.contains("ID, STREET, CITY, ZIP_CODE, COUNTRY, WITHIN_GEOMETRY;"));
        assertTrue(generatedCode.contains("case STREET -> pm.getDirectMapping(\"street\", true).get().dtoFieldType();"));
        assertTrue(generatedCode.contains("case WITHIN_GEOMETRY -> Object.class;"));
        assertTrue(generatedCode.contains("case ID -> Set.of(Op.EQ, Op.NE, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.IN, Op.RANGE);"));
        assertTrue(generatedCode.contains("case WITHIN_GEOMETRY -> Set.of(Op.MATCHES);"));
        assertTrue(generatedCode.contains("return ProjectionRegistry.getMetadataFor(io.github.cyfko.example.AddressDTO.class).entityClass();"));

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
                JavaFileObjects.forResource("testdata/UserPipes.java"),
                JavaFileObjects.forResource("testdata/VirtualFields.java")
        };
    }

    private String getGeneratedControllerCode(Compilation compilation) throws IOException {
        return compilation
                .generatedSourceFile("io.github.cyfko.filterql.spring.controller.FilterQlController")
                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                .getCharContent(true)
                .toString();
    }

    private String getGeneratedPersonEnumCode(Compilation compilation) throws IOException {
        return compilation
                .generatedSourceFile("io.github.cyfko.example.PersonDTO_")
                .orElseThrow(() -> new AssertionError("Generated PersonDTO_ enum not found"))
                .getCharContent(true)
                .toString();
    }

    private String getGeneratedAddressEnumCode(Compilation compilation) throws IOException {
        return compilation
                .generatedSourceFile("io.github.cyfko.example.AddressDTO_")
                .orElseThrow(() -> new AssertionError("Generated AddressDTO_ enum not found"))
                .getCharContent(true)
                .toString();
    }
}
