/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.springboot.docling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class DoclingServeRouteTest {

    private static final String SAMPLE_PDF = "sample-document.pdf";
    private static final String EXPECTED_MD = "expected-sample-document.md";
    private static final String OUTPUT_MD = "sample-document.md";
    private static final String OUTPUT_JSON = "sample-document.json";
    private static final String EXPECTED_JSON = "expected-sample-document.json";

    private static final Path RESOURCES_DIR = Paths.get("src/main/resources");
    private static final Path DOCUMENTS_DIR = RESOURCES_DIR.resolve("documents");
    private static final Path DOCUMENTS_EXTRACT_DIR = RESOURCES_DIR.resolve("documents/extract");
    private static final Path OUTPUT_DIR = RESOURCES_DIR.resolve("output");
    private static final Path OUTPUT_METADATA_DIR = RESOURCES_DIR.resolve("output/metadata");
    private static final Path SAMPLE_PDF_SOURCE = RESOURCES_DIR.resolve(SAMPLE_PDF);
    private static final Path EXPECTED_MD_SOURCE = RESOURCES_DIR.resolve(EXPECTED_MD);
    private static final Path EXPECTED_JSON_SOURCE = RESOURCES_DIR.resolve(EXPECTED_JSON);

    private Path testPdfPath;
    private Path testOutputPath;
    private Path testExtractPdfPath;
    private Path testMetadataOutputPath;

    @Container
    static GenericContainer<?> doclingContainer = new GenericContainer<>(
            DockerImageName.parse("quay.io/docling-project/docling-serve:v1.15.0"))
            .withExposedPorts(5001)
            .waitingFor(Wait.forHttp("/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void configureDoclingUrl(DynamicPropertyRegistry registry) {
        String doclingUrl = String.format("http://%s:%d",
                doclingContainer.getHost(),
                doclingContainer.getMappedPort(5001));
        registry.add("docling.serve.url", () -> doclingUrl);
    }

    @BeforeEach
    public void setUp() throws IOException {
        testPdfPath = DOCUMENTS_DIR.resolve(SAMPLE_PDF);
        testOutputPath = OUTPUT_DIR.resolve(OUTPUT_MD);
        testExtractPdfPath = DOCUMENTS_EXTRACT_DIR.resolve(SAMPLE_PDF);
        testMetadataOutputPath = OUTPUT_METADATA_DIR.resolve(OUTPUT_JSON);

        // Ensure the directories exist
        Files.createDirectories(DOCUMENTS_DIR);
        Files.createDirectories(DOCUMENTS_EXTRACT_DIR);
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(OUTPUT_METADATA_DIR);

        // Verify source PDF exists in src/main/resources
        assertTrue(Files.exists(SAMPLE_PDF_SOURCE),
                "Sample PDF must exist in src/main/resources: " + SAMPLE_PDF_SOURCE);

        // Clean up any existing test files
        cleanUpTestFiles();
    }

    @AfterEach
    public void tearDown() {
        cleanUpTestFiles();
    }

    @Test
    public void testDocumentConversionToMarkdown() throws IOException {
        // Copy the sample PDF from src/main/resources to the documents directory
        Files.copy(SAMPLE_PDF_SOURCE, testPdfPath, StandardCopyOption.REPLACE_EXISTING);
        assertTrue(Files.exists(testPdfPath), "Sample PDF should be copied to documents directory");

        // Wait for the file to be processed and converted to markdown
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertTrue(Files.exists(testOutputPath),
                            "Markdown output file should be created at: " + testOutputPath);
                    assertTrue(Files.size(testOutputPath) > 0,
                            "Markdown output file should not be empty");
                });

        // Verify the source PDF was deleted (as per route configuration with delete=true)
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertTrue(!Files.exists(testPdfPath),
                            "Source PDF should be deleted after processing");
                });

        // Optionally compare to expected output if it exists and is not a placeholder
        if (Files.exists(EXPECTED_MD_SOURCE)) {
            String expectedContent = Files.readString(EXPECTED_MD_SOURCE);
            // Only do comparison if expected file is not a placeholder
            if (!expectedContent.contains("placeholder")) {
                String actualContent = Files.readString(testOutputPath);
                assertEquals(expectedContent.trim(), actualContent.trim(),
                        "Generated markdown should match expected output");
            }
        }
    }

    @Test
    public void testDocumentMetadataExtraction() throws IOException {
        // Copy the sample PDF from src/main/resources to the documents/extract directory
        Files.copy(SAMPLE_PDF_SOURCE, testExtractPdfPath, StandardCopyOption.REPLACE_EXISTING);
        assertTrue(Files.exists(testExtractPdfPath),
                "Sample PDF should be copied to documents/extract directory");

        // Wait for the file to be processed and metadata extracted to JSON
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertTrue(Files.exists(testMetadataOutputPath),
                            "JSON metadata file should be created at: " + testMetadataOutputPath);
                    assertTrue(Files.size(testMetadataOutputPath) > 0,
                            "JSON metadata file should not be empty");
                });

        // Verify the source PDF was deleted (as per route configuration with delete=true)
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertTrue(!Files.exists(testExtractPdfPath),
                            "Source PDF should be deleted after processing");
                });

        // Optionally compare to expected output if it exists and is not a placeholder
        if (Files.exists(EXPECTED_JSON_SOURCE)) {
            String expectedContent = Files.readString(EXPECTED_JSON_SOURCE);
            // Only do comparison if expected file is not a placeholder
            if (!expectedContent.contains("placeholder")) {
                String actualContent = Files.readString(testMetadataOutputPath);
                assertEquals(expectedContent.trim(), actualContent.trim(),
                        "Generated JSON metadata should match expected output");
            }
        }
    }

    private void cleanUpTestFiles() {
        try {
            if (testPdfPath != null && Files.exists(testPdfPath)) {
                Files.delete(testPdfPath);
            }
            if (testOutputPath != null && Files.exists(testOutputPath)) {
                Files.delete(testOutputPath);
            }
            if (testExtractPdfPath != null && Files.exists(testExtractPdfPath)) {
                Files.delete(testExtractPdfPath);
            }
            if (testMetadataOutputPath != null && Files.exists(testMetadataOutputPath)) {
                Files.delete(testMetadataOutputPath);
            }
        } catch (IOException e) {
            System.err.println("Failed to clean up test files: " + e.getMessage());
        }
    }
}
