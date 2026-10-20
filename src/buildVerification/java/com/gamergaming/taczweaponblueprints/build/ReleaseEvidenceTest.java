package com.gamergaming.taczweaponblueprints.build;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the production evidence script and real Gradle task outcomes, without NeoForge. */
class ReleaseEvidenceTest {
    @TempDir Path project;
    Path source;

    @BeforeEach void fixture() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'release-evidence-fixture'\n");
        Files.writeString(project.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx256m\n");
        Files.copy(Path.of(System.getProperty("wrb.releaseEvidenceScript")), project.resolve("release-evidence.gradle"));
        String jars = Arrays.stream(System.getProperty("wrb.fixtureTestClasspath").split(File.pathSeparator))
                .map(ReleaseEvidenceTest::quote).collect(java.util.stream.Collectors.joining(","));
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'java' }
                dependencies { testImplementation files(%s) }
                test {
                    useJUnitPlatform()
                    maxHeapSize = '128m'
                    if (project.hasProperty('disabledTest')) enabled = false
                    if (project.hasProperty('skipTest')) onlyIf { false }
                    if (project.hasProperty('noTests')) testClassesDirs = files()
                    if (project.hasProperty('filteredTest')) filter.includeTestsMatching('GateFixture.*')
                }
                tasks.register('verifyReleaseArtifact') { dependsOn compileJava }
                tasks.register('verifyPackagedRuntimeEvidence') {
                    doLast { if (project.hasProperty('badRuntime')) throw new GradleException('stale runtime fixture') }
                }
                tasks.register('verifyVersionState') {
                    doLast { if (project.hasProperty('badVersion')) throw new GradleException('bad version fixture') }
                }
                tasks.register('certifyReleaseCandidate') {
                    dependsOn 'verifyReleaseArtifact', 'verifyRegressionEvidence', 'verifyPackagedRuntimeEvidence'
                    doLast { file('build/reports/release/candidate.json').text = '{"passed":true}' }
                }
                tasks.register('verifyPublicationReadiness') {
                    dependsOn 'certifyReleaseCandidate', 'verifyVersionState'
                    doLast { file('build/reports/release/publication.json').text = '{"passed":true}' }
                }
                apply from: 'release-evidence.gradle'
                """.formatted(jars));
        source = project.resolve("src/test/java/GateFixture.java");
        Files.createDirectories(source.getParent());
        // Enough genuine, tiny JUnit invocations to satisfy the production completeness floor.
        Files.writeString(source, """
                import org.junit.jupiter.api.RepeatedTest;
                import static org.junit.jupiter.api.Assertions.*;
                class GateFixture {
                    @RepeatedTest(2700) void regression() { assertEquals(1, 1); }
                }
                """);
    }

    @Test void excludedDisabledSkippedAndEmptyTasksCannotReuseOldXml() throws Exception {
        for (String[] args : List.of(new String[]{"-x", "test"}, new String[]{"-x", ":test"},
                new String[]{"-PdisabledTest"}, new String[]{"-PskipTest"}, new String[]{"-PnoTests"})) {
            seedOldEvidence();
            var result = run(false, concat("verifyRegressionEvidence", args));
            assertTrue(result.getOutput().contains("excluded, disabled, or skipped tests"), result.getOutput());
            assertReportsAbsent();
        }
    }

    @Test void currentSuccessfulUpToDateAndCachedTestsPassButChangedOrFilteredTestsDoNot() throws Exception {
        assertEquals(TaskOutcome.SUCCESS, run(true, "verifyRegressionEvidence", "--build-cache").task(":test").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, run(true, "verifyRegressionEvidence", "--build-cache").task(":test").getOutcome());
        assertEquals(TaskOutcome.FROM_CACHE, run(true, "clean", "verifyRegressionEvidence", "--build-cache").task(":test").getOutcome());
        assertTrue(Files.readString(report("regression")).contains("2700"));
        assertTrue(run(false, "verifyRegressionEvidence", "-PfilteredTest").getOutput().contains("complete test suite"));
        seedOldEvidence();
        Files.writeString(source, Files.readString(source).replace("assertEquals(1, 1)", "assertEquals(1, 2)")
                .replace("@RepeatedTest(2700)", "@RepeatedTest(1)"));
        var failed = run(false, "verifyRegressionEvidence");
        assertEquals(TaskOutcome.FAILED, failed.task(":test").getOutcome());
        assertReportsAbsent();
    }

    @Test void reportsAreInvalidatedBeforeCompilationRuntimeAndVersionFailures() throws Exception {
        run(true, "verifyPublicationReadiness");
        assertTrue(Files.exists(report("candidate")) && Files.exists(report("publication")));
        run(true, "help");
        assertTrue(Files.exists(report("candidate")), "Ordinary development commands do not invalidate evidence");

        var compileSource = project.resolve("src/main/java/Broken.java");
        Files.createDirectories(compileSource.getParent());
        Files.writeString(compileSource, "this is deliberately invalid Java");
        assertEquals(TaskOutcome.FAILED, run(false, "certifyReleaseCandidate").task(":compileJava").getOutcome());
        assertReportsAbsent();
        Files.delete(compileSource);

        for (String failure : List.of("-PbadRuntime", "-PbadVersion")) {
            seedOldEvidence();
            var result = run(false, "verifyPublicationReadiness", failure);
            assertEquals(TaskOutcome.SUCCESS, result.task(":invalidateReleaseReports").getOutcome());
            if (Files.exists(report("candidate")))
                assertFalse(Files.readString(report("candidate")).contains("old"), "Only a new successful certificate may remain");
            assertFalse(Files.exists(report("publication")));
        }
    }

    private BuildResult run(boolean succeeds, String... args) {
        var arguments = new ArrayList<>(List.of("--offline", "--max-workers=2", "--stacktrace"));
        arguments.addAll(List.of(args));
        var runner = GradleRunner.create().withProjectDir(project.toFile()).withArguments(arguments)
                .withTestKitDir(new File(System.getProperty("wrb.testKitDirectory")));
        return succeeds ? runner.build() : runner.buildAndFail();
    }
    private void seedOldEvidence() throws Exception {
        Files.createDirectories(report("candidate").getParent());
        for (String name : List.of("artifact", "regression", "runtime", "candidate", "publication"))
            Files.writeString(report(name), "{\"passed\":true,\"old\":true}");
        var xml = project.resolve("build/test-results/test/TEST-old.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, "<testsuite tests=\"2926\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
    }
    private void assertReportsAbsent() {
        for (String name : List.of("artifact", "regression", "runtime", "candidate", "publication"))
            assertFalse(Files.exists(report(name)), name + " must not retain a previous success");
    }
    private Path report(String name) { return project.resolve("build/reports/release/" + name + ".json"); }
    private static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
    private static String[] concat(String task, String[] args) {
        var result = new ArrayList<>(List.of(task)); result.addAll(List.of(args)); return result.toArray(String[]::new);
    }
}
