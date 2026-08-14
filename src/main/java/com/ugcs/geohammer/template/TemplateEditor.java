package com.ugcs.geohammer.template;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.template.model.FileSample;
import com.ugcs.geohammer.template.view.TemplateEditorView;
import com.ugcs.geohammer.util.Check;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
public class TemplateEditor {

    private final TemplateEditorController controller;

    private final TemplateEditorView view;

    public TemplateEditor(TemplateEditorController controller, TemplateEditorView view) {
        this.controller = controller;
        this.view = view;
    }

    // shows the editor dialog and waits until a template is applied
    // or the dialog is closed; returns null when cancelled;
    // synchronized: the editor is shared, sessions do not overlap
    public synchronized @Nullable Template createTemplate(File file) throws IOException {
        Check.notNull(file);
        Check.condition(!Platform.isFxApplicationThread(),
                "Template editor cannot be awaited on the application thread");

        FileSample fileSample = FileSample.read(file);
        CompletableFuture<@Nullable Template> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                controller.load(fileSample);
                view.show(result);
            } catch (RuntimeException e) {
                // complete exceptionally: an incomplete result would
                // block the awaiting loader thread forever
                result.completeExceptionally(e);
            }
        });
        return result.join();
    }
}
