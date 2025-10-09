/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.utilities;

import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import javax.tools.*;
import java.net.*;
import java.util.*;
import java.io.*;
import java.nio.file.Files;

/**
 *
 * @author daviestobialex
 */
public class RuntimeOpenApiGenerator {

    private RuntimeOpenApiGenerator() {
        throw new IllegalStateException("this function cannot be istantiated");
    }

    public static File generateFromString(String openApiSpec, String outputDir) throws Exception {
        // Write spec to temp file
        File specFile = File.createTempFile("openapi", ".json");
        Files.writeString(specFile.toPath(), openApiSpec);

        CodegenConfigurator configurator = new CodegenConfigurator();
        configurator.setInputSpec(specFile.getAbsolutePath());
        configurator.setGeneratorName("java"); // or "feign"
        configurator.setOutputDir(outputDir);
        configurator.addAdditionalProperty("dateLibrary", "java8");
        configurator.addAdditionalProperty("hideGenerationTimestamp", true);

        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(configurator.toClientOptInput());
        generator.generate();

        return new File(outputDir);
    }

    public static ClassLoader compileAndLoad(File sourceDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        List<File> files = new ArrayList<>();
        Files.walk(sourceDir.toPath())
                .filter(f -> f.toString().endsWith(".java"))
                .forEach(f -> files.add(f.toFile()));

        List<String> options = List.of("-classpath", System.getProperty("java.class.path"));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits
        );

        boolean success = task.call();
        fileManager.close();

        if (!success) {
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                System.err.println(d);
            }
            throw new IllegalStateException("Compilation failed");
        }

        return new URLClassLoader(new URL[]{sourceDir.toURI().toURL()});
    }
}
