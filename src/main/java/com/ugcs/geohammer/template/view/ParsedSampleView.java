package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.TemplateEditorController;
import com.ugcs.geohammer.template.ValueParser;
import com.ugcs.geohammer.template.model.ColumnModel;
import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.ColumnsModel;
import com.ugcs.geohammer.template.model.ParsedSample;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.ReentranceGuard;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import org.jspecify.annotations.Nullable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ParsedSampleView extends TableView<List<String>> {

    private static final double COLUMN_WIDTH = 152;

    private final TemplateModel templateModel;

    private final TemplateEditorController templateEditorController;

    private final ValueParser valueParser;

    private final List<ColumnMapping> columnMappings = new ArrayList<>();

    private final ReentranceGuard columnsGuard = new ReentranceGuard();

    public ParsedSampleView(
            TemplateModel templateModel,
            TemplateEditorController templateEditorController,
            ValueParser valueParser) {
        this.templateModel = templateModel;
        this.templateEditorController = templateEditorController;
        this.valueParser = valueParser;

        setPlaceholder(new Label("No columns found"));
        setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Listeners.onChange(templateModel.parsedSampleProperty(), this::updateSample);
        initModelRefresh();
        updateSample(templateModel.getParsedSample());
    }

    private void updateSample(ParsedSample parsedSample) {
        // headers unchanged: keep the column controls bound to the reused
        // column models, so stale model listeners do not pile up
        if (!parsedSample.headers().equals(mappedHeaders())) {
            rebuildColumns(parsedSample);
        }
        getItems().setAll(parsedSample.rows());
    }

    private List<String> mappedHeaders() {
        List<String> headers = new ArrayList<>(columnMappings.size());
        for (ColumnMapping mapping : columnMappings) {
            headers.add(mapping.header());
        }
        return headers;
    }

    private void rebuildColumns(ParsedSample parsedSample) {
        columnMappings.clear();

        List<TableColumn<List<String>, String>> columns = new ArrayList<>(parsedSample.headers().size());
        for (int i = 0; i < parsedSample.headers().size(); i++) {
            int index = i;
            String header = parsedSample.headers().get(i);
            ColumnMapping mapping = createColumnMapping(header);
            columnMappings.add(mapping);

            // mapping controls live in a nested column header
            TableColumn<List<String>, String> valueColumn = new TableColumn<>();
            valueColumn.getStyleClass().add("subheader");
            valueColumn.setGraphic(mapping.view());
            valueColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueAt(cell.getValue(), index)));
            valueColumn.setCellFactory(c -> createValueCell(header));
            valueColumn.setPrefWidth(COLUMN_WIDTH);
            valueColumn.setSortable(false);

            TableColumn<List<String>, String> column = new TableColumn<>();
            column.setGraphic(createHeaderCell(header, mapping.summary()));
            column.setSortable(false);
            column.getColumns().add(valueColumn);
            columns.add(column);
        }
        getColumns().setAll(columns);
        updateSummaries();
    }

    private Node createHeaderCell(String header, Label summary) {
        Label name = new Label(header);

        HBox headerCell = new HBox(4, name, summary);
        headerCell.setAlignment(Pos.BASELINE_LEFT);
        // keeps the pref height, so the header centers it vertically
        headerCell.setMaxHeight(Region.USE_PREF_SIZE);
        return headerCell;
    }

    private ColumnMapping createColumnMapping(String header) {
        ColumnsModel columns = templateModel.getColumns();

        ComboBox<ColumnType> typeSelector = new ComboBox<>(
                FXCollections.observableArrayList(ColumnType.values()));
        typeSelector.setPrefWidth(COLUMN_WIDTH - 40);
        typeSelector.setValue(columns.getColumnType(header));

        ColumnModel column = columns.getColumn(header);
        ColumnOptions options = new ColumnOptions(
                column != null ? column : new ColumnModel(header));
        options.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !ColumnOptions.supportsOptions(typeSelector.getValue()),
                typeSelector.valueProperty()));

        Label summary = new Label();
        summary.getStyleClass().add("column-summary");

        Listeners.onChange(typeSelector.valueProperty(), type -> {
            if (columnsGuard.isLatched()) {
                return;
            }
            templateEditorController.setColumnType(header, type != null ? type : ColumnType.NONE);
        });

        HBox view = new HBox(4, typeSelector, options);
        view.setAlignment(Pos.CENTER_LEFT);
        return new ColumnMapping(header, typeSelector, summary, view);
    }

    private void updateColumnTypes() {
        columnsGuard.run(() -> {
            ColumnsModel columns = templateModel.getColumns();
            for (ColumnMapping mapping : columnMappings) {
                ColumnType type = columns.getColumnType(mapping.header());
                if (mapping.typeSelector().getValue() != type) {
                    mapping.typeSelector().setValue(type);
                }
            }
        });
    }

    private void updateSummaries() {
        ColumnsModel columns = templateModel.getColumns();
        for (ColumnMapping mapping : columnMappings) {
            String summary = createSummaryText(columns.getColumn(mapping.header()));
            mapping.summary().setText(Strings.isNullOrEmpty(summary)
                    ? Strings.empty()
                    : "(" + summary + ")");
        }
    }

    private static String createSummaryText(@Nullable ColumnModel column) {
        if (column == null) {
            return Strings.empty();
        }
        return switch (column.getType()) {
            case DATE_TIME, DATE, TIME -> String.join(", ", column.getFormats());
            case VALUE -> {
                List<String> parts = new ArrayList<>(2);
                if (!Strings.isNullOrBlank(column.getUnits())) {
                    parts.add(Strings.trim(column.getUnits()));
                }
                parts.add(Integer.toString(column.getDecimals()));
                yield String.join(", ", parts);
            }
            default -> Strings.empty();
        };
    }

    private TableCell<List<String>, String> createValueCell(String header) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle(Strings.empty());
                    return;
                }
                ValueParser.Preview preview = valueParser.parse(header, item);
                setText(preview.text());
                setStyle(switch (preview.status()) {
                    case PARSED -> "-fx-text-fill: -color-ok;";
                    case ERROR -> "-fx-text-fill: -color-error;";
                    case PLAIN -> Strings.empty();
                });
            }
        };
    }

    private void initModelRefresh() {
        templateModel.getColumns().getColumns().addListener(
                (ListChangeListener<ColumnModel>)change -> {
                    updateColumnTypes();
                    updateSummaries();
                    refresh();
                });
    }

    private static String valueAt(List<String> row, int index) {
        return index < row.size() ? row.get(index) : Strings.empty();
    }

    private record ColumnMapping(
            String header,
            ComboBox<ColumnType> typeSelector,
            Label summary,
            Node view) {
    }
}
