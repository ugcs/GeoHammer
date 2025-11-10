package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.parsers.Column;
import com.ugcs.gprvisualizer.app.parsers.ColumnSchema;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.FileUpdatedEvent;
import com.ugcs.gprvisualizer.event.SeriesUpdatedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.app.service.TemplateSettings;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.ColorPalette;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Templates;
import com.ugcs.gprvisualizer.utils.Views;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class SeriesSelectorView extends VBox implements InitializingBean {

    private static final String DEFAULT_TITLE = "Select chart";

    private static final ButtonType REMOVE_SERIES_IN_FILE
            = new ButtonType("Current file", ButtonBar.ButtonData.LEFT);

    private static final ButtonType REMOVE_SERIES_IN_TEMPLATE_FILES
            = new ButtonType("All files", ButtonBar.ButtonData.LEFT);

    private final Model model;

    private final TemplateSettings templateSettings;

    @Nullable
    private Template selectedTemplate;

    private Label title;

    private Button removeSeriesButton;

    private ObservableList<SeriesMeta> series = FXCollections.observableArrayList();

    private ComboBox<SeriesMeta> seriesSelector;

    private ChangeListener<SeriesMeta> seriesChangeListener;

    public record SeriesMeta(String name, String style, BooleanProperty visible) {

        public SeriesMeta(String name, Color color, BooleanProperty visible) {
            this(name, "-fx-text-fill: " + Views.toColorString(color) + ";", visible);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public SeriesSelectorView(Model model, TemplateSettings templateSettings) {
        Check.notNull(model);
        Check.notNull(templateSettings);

        this.model = model;
        this.templateSettings = templateSettings;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initView();
    }

    private void initView() {
        initSeriesSelector();

        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.setSpacing(5);

        title = new Label(DEFAULT_TITLE);
        title.setStyle("-fx-text-fill: #dddddd;");

        removeSeriesButton = ResourceImageHolder.setButtonImage(
                ResourceImageHolder.DELETE,
                Color.web("#666666"),
                26,
                new Button());
        removeSeriesButton.setTooltip(new Tooltip("Remove selected column"));
        removeSeriesButton.setOnAction(this::onRemoveSeries);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        container.getChildren().addAll(title, spacer, seriesSelector, removeSeriesButton);
        getChildren().addAll(container);
    }

    private void updateSeriesRemovalButton() {
        SeriesMeta selectedSeries = seriesSelector.getValue();
        if (selectedSeries == null) {
            removeSeriesButton.setDisable(true);
            return;
        }
        // column should not be read-only
        boolean disable = true;
        if (model.getCurrentFile() instanceof CsvFile csvFile) {
            ColumnSchema schema = GeoData.getSchema(csvFile.getGeoData());
            if (schema != null) {
                Column column = schema.getColumn(selectedSeries.name);
                if (column != null && !column.isReadOnly()) {
                    disable = false;
                }
            }
        }
        removeSeriesButton.setDisable(disable);
    }

    private ButtonType confirmSeriesRemoval(@Nullable Template template, String seriesName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm column removal");
        alert.setHeaderText("Remove column '" + seriesName + "'?");

        List<ButtonType> buttons = new ArrayList<>(3);
        buttons.add(REMOVE_SERIES_IN_FILE);
        if (numOpenFiles(template) > 1) {
            buttons.add(REMOVE_SERIES_IN_TEMPLATE_FILES);
        }
        buttons.add(ButtonType.CANCEL);
        alert.getButtonTypes().setAll(buttons);

        return alert.showAndWait().orElse(ButtonType.CANCEL);
    }

    private void onRemoveSeries(ActionEvent event) {
        SeriesMeta selectedSeries = seriesSelector.getValue();
        if (selectedSeries == null) {
            return;
        }
        Template template = selectedTemplate;
        ButtonType confirmation = confirmSeriesRemoval(template, selectedSeries.name);
        if (Objects.equals(confirmation, REMOVE_SERIES_IN_FILE)) {
            if (model.getCurrentFile() instanceof CsvFile csvFile) {
                removeChart(csvFile, selectedSeries.name);
            };
        }
        if (Objects.equals(confirmation, REMOVE_SERIES_IN_TEMPLATE_FILES)) {
            for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
                if (Templates.equals(csvFile.getTemplate(), template)) {
                    removeChart(csvFile, selectedSeries.name);
                }
            }
        }
    }

    private void initSeriesSelector() {
        series = FXCollections.observableArrayList();

        seriesSelector = new ComboBox<>(series) {
            @Override
            protected Skin<?> createDefaultSkin() {
                Skin<?> skin = super.createDefaultSkin();
                if (skin instanceof ComboBoxListViewSkin<?> listViewSkin) {
                    // don't hide on item click
                    listViewSkin.setHideOnClick(false);
                }
                return skin;
            }
        };

        // style of item in a drop-down list
        seriesSelector.setCellFactory(listView
                -> new CheckBoxListCell<>(series -> series.visible) {
            @Override
            public void updateItem(SeriesMeta item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                    setStyle(item.style);
                }
            }
        });

        // style of item in a collapsed selector box
        seriesSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SeriesMeta item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name);
                    setStyle(item.style);
                }
            }
        });
    }

    private void selectTemplate(@Nullable Template template) {
        // dispose change listener
        clearSeriesChangeListener();

        if (template == null) {
            title.setText(DEFAULT_TITLE);
            series.clear();
        } else {
            title.setText(template.getName());
            series.setAll(getSeriesMetas(template));

            // get selected series from settings
            String selectedSeriesName = templateSettings.getSelectedSeriesName(template);
            SeriesMeta selectedSeries = getSeriesByName(selectedSeriesName);
            // select first visible item in the list
            if (selectedSeries == null) {
                for (SeriesMeta series : series) {
                    if (series.visible.get()) {
                        selectedSeries = series;
                        break;
                    }
                }
            }
            // select first item in the list
            if (selectedSeries == null && !series.isEmpty()) {
                selectedSeries = series.getFirst();
            }
            if (selectedSeries != null) {
                seriesSelector.setValue(selectedSeries);
                updateSeriesRemovalButton();
            }

            updateCharts(template);

            // init series selection listener
            setSeriesChangeListener(template);
        }
    }

    private void updateCharts(Template template) {
        // update charts visibility
        for (SeriesMeta series : series) {
            showChart(template, series.name, series.visible.get());
        }
        // set selected chart
        SeriesMeta selectedSeries = seriesSelector.getValue();
        selectChart(template, selectedSeries != null ? selectedSeries.name : null);
    }

    private @Nullable SeriesMeta getSeriesByName(@Nullable String seriesName) {
        if (Strings.isNullOrEmpty(seriesName)) {
            return null;
        }
        for (SeriesMeta series : series) {
            if (Objects.equals(series.name, seriesName)) {
                return series;
            }
        }
        return null;
    }

    private void clearSeriesChangeListener() {
        if (seriesChangeListener == null) {
            return;
        }
        seriesSelector.getSelectionModel()
                .selectedItemProperty()
                .removeListener(seriesChangeListener);
        seriesChangeListener = null;
    }

    private void setSeriesChangeListener(Template template) {
        Check.notNull(template);

        seriesChangeListener = createSeriesChangeListener(template);
        seriesSelector.getSelectionModel()
                .selectedItemProperty()
                .addListener(seriesChangeListener);
    }

    private boolean hasOpenFiles(@Nullable Template template) {
        if (template == null) {
            return false;
        }
        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (Templates.equals(csvFile.getTemplate(), template)) {
                return true;
            }
        }
        return false;
    }

    private int numOpenFiles(@Nullable Template template) {
        if (template == null) {
            return 0;
        }
        int numFiles = 0;
        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (Templates.equals(csvFile.getTemplate(), template)) {
                numFiles++;
            }
        }
        return numFiles;
    }

    private Set<String> getSeriesNames(@Nullable Template template) {
        if (template == null) {
            return new HashSet<>();
        }
        Set<String> seriesNames = new HashSet<>();
        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (csvFile == null) {
                continue;
            }
            if (Templates.equals(csvFile.getTemplate(), template)) {
                model.getCsvChart(csvFile).ifPresent(chart
                        -> seriesNames.addAll(chart.getSeriesNames()));
            }
        }
        return seriesNames;
    }

    private List<String> orderSeriesNames(Set<String> seriesNames) {
        List<String> orderedSeriesNames = new ArrayList<>(seriesNames);
        Collections.sort(orderedSeriesNames);
        return orderedSeriesNames;
    }

    private Map<String, Boolean> getVisibilitySettings(Template template, Set<String> seriesNames) {
        Check.notNull(template);
        Check.notNull(seriesNames);

        Map<String, Boolean> visibilitySettings = new HashMap<>();
        for (String seriesName : seriesNames) {
            Boolean seriesVisible = templateSettings.isSeriesVisible(template, seriesName);
            if (seriesVisible == null) {
                continue;
            }
            visibilitySettings.put(seriesName, seriesVisible);
        }
        // if no settings defined for template, make first series visible
        if (visibilitySettings.isEmpty() && !seriesNames.isEmpty()) {
            // try template-defined column
            for (SensorData column : template.getDataMapping().getDataValues()) {
                String seriesName = column.getHeader();
                if (seriesNames.contains(seriesName)) {
                    visibilitySettings.put(seriesName, true);
                    break;
                }
            }
            // peek first in order
            if (visibilitySettings.isEmpty()) {
                visibilitySettings.put(orderSeriesNames(seriesNames).getFirst(), true);
            }
        }
        return visibilitySettings;
    }

    private List<SeriesMeta> getSeriesMetas(@Nullable Template template) {
        if (template == null) {
            return Collections.emptyList();
        }

        Set<String> seriesNames = getSeriesNames(template);
        Map<String, Boolean> visibilitySettings = getVisibilitySettings(template, seriesNames);

        List<SeriesMeta> seriesMetas = new ArrayList<>(seriesNames.size());
        for (String seriesName : orderSeriesNames(seriesNames)) {
            boolean seriesVisible = visibilitySettings.getOrDefault(seriesName, false);
            seriesMetas.add(toSeriesMeta(template, seriesName, seriesVisible));
        }
        return seriesMetas;
    }

    private SeriesMeta toSeriesMeta(Template template, String seriesName, boolean seriesVisible) {
        Check.notNull(template);
        Check.notEmpty(seriesName);

        return new SeriesMeta(
                seriesName,
                ColorPalette.highContrast().getColor(seriesName),
                createVisibleProperty(template, seriesName, seriesVisible));
    }

    private BooleanProperty createVisibleProperty(Template template, String seriesName, boolean seriesVisible) {
        Check.notNull(template);
        Check.notEmpty(seriesName);

        BooleanProperty visibleProperty = new SimpleBooleanProperty(seriesVisible);
        visibleProperty.addListener((observable, oldValue, newValue) -> {
            showChart(template, seriesName, newValue);
            templateSettings.setSeriesVisible(template, seriesName, newValue);
        });

        return visibleProperty;
    }

    private ChangeListener<SeriesMeta> createSeriesChangeListener(Template template) {
        Check.notNull(template);
        return (observable, oldValue, newValue) -> {
            if (Objects.equals(oldValue, newValue)) {
                return; // do nothing
            }
            String selectedSeriesName = null;
            if (newValue != null) {
                selectedSeriesName = newValue.name;
            }
            selectChart(template, selectedSeriesName);
            templateSettings.setSelectedSeriesName(template, selectedSeriesName);
            updateSeriesRemovalButton();
        };
    }

    // chart updates

    private void removeChart(CsvFile csvFile, String seriesName) {
        model.getCsvChart(csvFile).ifPresent(chart -> {
            chart.removeFileColumn(seriesName);
        });
    }

    private void selectChart(Template template, @Nullable String seriesName) {
        Check.notNull(template);

        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (Templates.equals(csvFile.getTemplate(), template)) {
                SensorLineChart chart = model.getCsvChart(csvFile).orElse(null);
                if (chart != null) {
                    chart.selectChart(seriesName);
                }
            }
        }
    }

    private void showChart(Template template, String seriesName, boolean show) {
        Check.notNull(template);
        Check.notEmpty(seriesName);

        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (Templates.equals(csvFile.getTemplate(), template)) {
                SensorLineChart chart = model.getCsvChart(csvFile).orElse(null);
                if (chart != null) {
                    chart.showChart(seriesName, show);
                }
            }
        }
    }

    // events

    private boolean reloadTemplateOnChange(SgyFile file) {
        if (selectedTemplate == null) {
            return false;
        }
        if (file instanceof CsvFile csvFile) {
            Template template = csvFile.getTemplate();
            if (Templates.equals(template, selectedTemplate)) {
                // reload template
                Platform.runLater(() -> {
                    selectTemplate(template);
                });
                return true;
            }
        }
        return false;
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Template template = null;
        if (event.getFile() instanceof CsvFile csvFile) {
            template = csvFile.getTemplate();
            if (!hasOpenFiles(template)) {
                template = null;
            }
        }
        if (!Templates.equals(template, selectedTemplate)) {
            selectedTemplate = template;
            Template templateRef = template; // final reference
            Platform.runLater(() -> {
                selectTemplate(templateRef);
            });
        }
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        if (selectedTemplate == null) {
            return;
        }

        Set<File> openedFiles = new HashSet<>(event.getFiles());
        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (openedFiles.contains(csvFile.getFile())) {
                if (reloadTemplateOnChange(csvFile)) {
                    break;
                }
            }
        }
    }

	@EventListener
	private void onFileUpdated(FileUpdatedEvent event) {
        reloadTemplateOnChange(event.getSgyFile());
	}

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        reloadTemplateOnChange(event.getSgyFile());
    }

    @EventListener
    private void onSeriesUpdated(SeriesUpdatedEvent event) {
        Template template = event.getFile().getTemplate();
        if (!Templates.equals(template, selectedTemplate)) {
            return;
        }
        String seriesName = event.getSeriesName();
        Platform.runLater(() -> {
            SeriesMeta series = getSeriesByName(seriesName);
            if (series == null) {
                selectTemplate(template);
                series = getSeriesByName(seriesName);
                if (series == null) {
                    return;
                }
            }
            series.visible.setValue(event.isSeriesVisible());
            showChart(template, seriesName, event.isSeriesVisible());
            if (event.isSeriesSelected()) {
                seriesSelector.setValue(series);
                selectChart(template, seriesName);
            }
        });
    }
}
