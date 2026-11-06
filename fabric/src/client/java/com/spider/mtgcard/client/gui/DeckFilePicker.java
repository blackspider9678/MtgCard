package com.spider.mtgcard.client.gui;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Native file dialogs: Minecraft deliberately runs Java AWT in headless mode. */
public final class DeckFilePicker {
    private DeckFilePicker() {}

    public static List<Path> open() throws IOException, InterruptedException {
        List<Path> paths = new ArrayList<>();
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            // Windows' native dialog supports omitting the All Files filter entirely.
            // A separate STA process also avoids Minecraft's headless Java AWT state.
            String script = """
                    $ErrorActionPreference = 'Stop'
                    Add-Type -AssemblyName System.Windows.Forms
                    $dialog = New-Object System.Windows.Forms.OpenFileDialog
                    $dialog.Title = 'Import Deck'
                    $dialog.Filter = 'Deck files (*.txt;*.csv)|*.txt;*.csv'
                    $dialog.Multiselect = $true
                    $dialog.CheckFileExists = $true
                    $owner = New-Object System.Windows.Forms.Form
                    $owner.ShowInTaskbar = $false
                    $owner.TopMost = $true
                    $owner.Opacity = 0
                    $owner.Show()
                    try {
                        if ($dialog.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) {
                            foreach ($file in $dialog.FileNames) {
                                'DECK:' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($file))
                            }
                        }
                    } finally { $dialog.Dispose(); $owner.Dispose() }
                    """;
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            Process process = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
                    "-STA", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new IOException("Native file picker failed: " + output.strip());
            for (String line : output.lines().toList()) {
                if (line.startsWith("DECK:")) {
                    paths.add(Path.of(new String(Base64.getDecoder().decode(line.substring(5)), StandardCharsets.UTF_8)));
                }
            }
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.txt")).put(stack.UTF8("*.csv")).flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import Deck", "", filters, "Deck files (*.txt, *.csv)", true);
                if (selected != null) {
                    for (String file : selected.split("\\|")) paths.add(Path.of(file));
                }
            }
        }
        for (Path path : paths) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!Files.isRegularFile(path) || !(name.endsWith(".txt") || name.endsWith(".csv"))) {
                throw new IOException("Choose .txt or .csv deck files.");
            }
        }
        return List.copyOf(paths);
    }
}
