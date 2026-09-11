package io.github.lazyimmortal.sesamewatchdog.logging;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DiagnosticLogFilesTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void appendsAndReadsLogs() throws Exception {
        File directory = temporaryFolder.newFolder("diagnostics");

        DiagnosticLogFiles.append(directory, "first\n");
        DiagnosticLogFiles.append(directory, "second\n");

        String logs = DiagnosticLogFiles.read(directory);
        assertTrue(logs.contains("first"));
        assertTrue(logs.contains("second"));
    }

    @Test
    public void rotatesAndKeepsOnlyOnePreviousFile() throws Exception {
        File directory = temporaryFolder.newFolder("diagnostics");
        File currentFile = new File(directory, DiagnosticLogFiles.CURRENT_FILE_NAME);
        Files.write(currentFile.toPath(), new byte[(int) DiagnosticLogFiles.MAX_FILE_BYTES]);

        DiagnosticLogFiles.append(directory, "new line\n");

        assertTrue(new File(directory, DiagnosticLogFiles.PREVIOUS_FILE_NAME).isFile());
        assertTrue(DiagnosticLogFiles.read(directory).contains("new line"));
        assertTrue(DiagnosticLogFiles.sizeBytes(directory)
                <= DiagnosticLogFiles.MAX_FILE_BYTES * 2L);
    }

    @Test
    public void clearsBothRotatedFiles() throws Exception {
        File directory = temporaryFolder.newFolder("diagnostics");
        DiagnosticLogFiles.append(directory, "current\n");
        Files.write(
                new File(directory, DiagnosticLogFiles.PREVIOUS_FILE_NAME).toPath(),
                "previous\n".getBytes());

        DiagnosticLogFiles.clear(directory);

        assertFalse(new File(directory, DiagnosticLogFiles.CURRENT_FILE_NAME).exists());
        assertFalse(new File(directory, DiagnosticLogFiles.PREVIOUS_FILE_NAME).exists());
    }
}
