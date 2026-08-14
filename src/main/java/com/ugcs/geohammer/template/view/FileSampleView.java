package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.model.FileSample;
import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileSampleView extends ListView<String> {

    private static final String LINE_STYLE = "-fx-font-family: monospace;";

    private static final String HEADER_LINE_STYLE = LINE_STYLE + "-fx-font-weight: bold;";

    private static final String SKIP_LINE_STYLE = LINE_STYLE + "-fx-text-fill: -color-text-dim;";

    private final TemplateModel templateModel;

    public FileSampleView(TemplateModel templateModel) {
        this.templateModel = templateModel;

        setCellFactory(view -> createLineCell());
        setPlaceholder(new Label("No file content"));

        Listeners.onChange(templateModel.fileSampleProperty(), this::updateSample);
        Listeners.onChange(templateModel.parsedSampleProperty(), v -> refresh());
        updateSample(templateModel.getFileSample());
    }

    public int getSelectedLine() {
        return getSelectionModel().getSelectedIndex();
    }

    private void updateSample(@Nullable FileSample fileSample) {
        getItems().setAll(fileSample != null ? fileSample.lines() : List.of());
    }

    private ListCell<String> createLineCell() {
        return new ListCell<>() {
            private final Label lineNumber = createLineNumber();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle(Strings.empty());
                    return;
                }

                ParsedSample parsedSample = templateModel.getParsedSample();

                int i = getIndex();
                int numSkipLines = parsedSample.numSkipLines();

                String style = LINE_STYLE;
                if (i < numSkipLines) {
                    style = SKIP_LINE_STYLE;
                } else if (i == numSkipLines && templateModel.getFormat().isHasHeader()) {
                    style = HEADER_LINE_STYLE;
                }

                lineNumber.setText(Integer.toString(getIndex() + 1));
                setGraphic(lineNumber);
                setText(item);
                setStyle(style);
            }
        };
    }

    private static Label createLineNumber() {
        Label lineNumber = new Label();
        lineNumber.getStyleClass().add("line-number");
        lineNumber.setPrefWidth(30);
        lineNumber.setAlignment(Pos.CENTER_RIGHT);
        return lineNumber;
    }
}
