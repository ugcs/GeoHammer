package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.FileUpdatedEvent;
import com.ugcs.geohammer.model.event.SeriesUpdatedEvent;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.TemplateSettings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.ColorPalette;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.view.Views;
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
public class SeriesSelectorView extends VBox {

    private static final String DEFAULT_TITLE = "Select chart";

    private static final ButtonType REMOVE_SERIES_IN_FILE
            = new ButtonType("Current file", ButtonBar.ButtonData.LEFT);

    private static final ButtonType REMOVE_SERIES_IN_TEMPLATE_FILES
            = new ButtonType("All files", ButtonBar.ButtonData.LEFT);

    private final Model model;

    private final FileTemplates templates;

    private final TemplateSettings templateSettings;

    @Nullable
    private String selectedTemplateName;

    private final Label title;

    private final Button removeSeriesButton;

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

    public SeriesSelectorView(Model model, FileTemplates templates, TemplateSettings templateSettings) {
        Check.notNull(model);
        Check.notNull(templates);
        Check.notNull(templateSettings);

        this.model = model;
        this.templates = templates;
        this.templateSettings = templateSettings;

        initSeriesSelector();

        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.setSpacing(Tools.DEFAULT_SPACING);

        title = new Label(DEFAULT_TITLE);
        title.setStyle("-fx-text-fill: #dddddd;");
        title.setMinHeight(26);

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

        showSeriesSelector(false);
    }

    private void updateSeriesRemovalButton() {
        SeriesMeta selectedSeries = seriesSelector.getValue();
        if (selectedSeries == null) {
            removeSeriesButton.setDisable(true);
            return;
        }
        // column should not be read-only
        boolean disable = true;
        SgyFile file = model.getCurrentFile();
        if (file != null) {
            ColumnSchema schema = GeoData.getSchema(file.getGeoData());
            if (schema != null) {
                Column column = schema.getColumn(selectedSeries.name);
                if (column != null && !column.isReadOnly()) {
                    disable = false;
                }
            }
        }
        removeSeriesButton.setDisable(disable);
    }

    private ButtonType confirmSeriesRemoval(@Nullable String templateName, String seriesName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm column removal");
        alert.setHeaderText("Remove column '" + seriesName + "'?");

        List<ButtonType> buttons = new ArrayList<>(3);
        buttons.add(REMOVE_SERIES_IN_FILE);
        if (numOpenFiles(templateName) > 1) {
            buttons.add(REMOVE_SERIES_IN_TEMPLATE_FILES);
        }
        buttons.add(ButtonType.CANCEL);
        alert.getButtonTypes().setAll(buttons);
        alert.initOwner(AppContext.stage);

        return alert.showAndWait().orElse(ButtonType.CANCEL);
    }

    private void onRemoveSeries(ActionEvent event) {
        SeriesMeta selectedSeries = seriesSelector.getValue();
        if (selectedSeries == null) {
            return;
        }
        String templateName = selectedTemplateName;
        ButtonType confirmation = confirmSeriesRemoval(templateName, selectedSeries.name);
        if (Objects.equals(confirmation, REMOVE_SERIES_IN_FILE)) {
            SgyFile file = model.getCurrentFile();
            if (file != null) {
                removeChart(file, selectedSeries.name);
            }
        }
        if (Objects.equals(confirmation, REMOVE_SERIES_IN_TEMPLATE_FILES)) {
            for (SgyFile file : model.getFileManager().getFiles()) {
                if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                    removeChart(file, selectedSeries.name);
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

            {
                setOnMouseEntered(e -> setStyle("-fx-text-fill: white;"));
                setOnMouseExited(e -> updateStyle(getItem()));
            }

            private void updateStyle(SeriesMeta item) {
                if (isSelected()) {
                    setStyle("-fx-text-fill: white;");
                } else if (item != null) {
                    setStyle(item.style);
                } else {
                    setStyle(Strings.empty());
                }
            }

            @Override
            public void updateItem(SeriesMeta item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(Strings.empty());
                } else {
                    setText(item.name);
                    updateStyle(item);
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

    private void showSeriesSelector(boolean show) {
        seriesSelector.setVisible(show);
        seriesSelector.setManaged(show);
        removeSeriesButton.setVisible(show);
        removeSeriesButton.setManaged(show);
    }

    private void selectTemplate(@Nullable String templateName) {
        // dispose change listener
        clearSeriesChangeListener();

        if (Strings.isNullOrEmpty(templateName)) {
            title.setText(DEFAULT_TITLE);
            series.clear();
            showSeriesSelector(false);
        } else {
            title.setText(templateName);
            List<SeriesMeta> seriesMetas = getSeriesMetas(templateName);
            series.setAll(seriesMetas);
            showSeriesSelector(!Nulls.isNullOrEmpty(seriesMetas));

            // get selected series from settings
            String selectedSeriesName = templateSettings.getSelectedSeriesName(templateName);
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

            updateCharts(templateName);

            // init series selection listener
            setSeriesChangeListener(templateName);
        }
    }

    private void updateCharts(String templateName) {
        // update charts visibility
        for (SeriesMeta series : series) {
            showChart(templateName, series.name, series.visible.get());
        }
        // set selected chart
        SeriesMeta selectedSeries = seriesSelector.getValue();
        selectChart(templateName, selectedSeries != null ? selectedSeries.name : null);
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

    private void setSeriesChangeListener(String templateName) {
        Check.notEmpty(templateName);

        seriesChangeListener = createSeriesChangeListener(templateName);
        seriesSelector.getSelectionModel()
                .selectedItemProperty()
                .addListener(seriesChangeListener);
    }

    private boolean hasOpenFiles(@Nullable String templateName) {
        if (Strings.isNullOrEmpty(templateName)) {
            return false;
        }
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                return true;
            }
        }
        return false;
    }

    private int numOpenFiles(@Nullable String templateName) {
        if (Strings.isNullOrEmpty(templateName)) {
            return 0;
        }
        int numFiles = 0;
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                numFiles++;
            }
        }
        return numFiles;
    }

    private Set<String> getSeriesNames(@Nullable String templateName) {
        if (Strings.isNullOrEmpty(templateName)) {
            return new HashSet<>();
        }
        Set<String> seriesNames = new HashSet<>();
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                Chart chart = model.getChart(file);
                if (chart instanceof SensorLineChart sensorChart) {
                    seriesNames.addAll(sensorChart.getSeriesNames());
                }
            }
        }
        return seriesNames;
    }

    private List<String> orderSeriesNames(Set<String> seriesNames) {
        List<String> orderedSeriesNames = new ArrayList<>(seriesNames);
        Collections.sort(orderedSeriesNames);
        return orderedSeriesNames;
    }

    private Map<String, Boolean> getVisibilitySettings(String templateName, Set<String> seriesNames) {
        Check.notEmpty(templateName);
        Check.notNull(seriesNames);

        Map<String, Boolean> visibilitySettings = new HashMap<>();
        for (String seriesName : seriesNames) {
            Boolean seriesVisible = templateSettings.isSeriesVisible(templateName, seriesName);
            if (seriesVisible == null) {
                continue;
            }
            visibilitySettings.put(seriesName, seriesVisible);
        }
        // if no settings defined for template, make default series visible
        if (visibilitySettings.isEmpty() && !seriesNames.isEmpty()) {
            String defaultSeriesName = getDefaultSeriesName(templateName, seriesNames);
            if (!Strings.isNullOrEmpty(defaultSeriesName)) {
                // make default series visible
                templateSettings.setSeriesVisible(templateName, defaultSeriesName, true);
                visibilitySettings.put(defaultSeriesName, true);
            }
        }
        return visibilitySettings;
    }

    private String getDefaultSeriesName(String templateName, Set<String> seriesNames) {
        if (Nulls.isNullOrEmpty(seriesNames)) {
            return null;
        }
        // try template-defined column
        Template template = templates.getTemplate(templateName);
        if (template != null) {
            for (SensorData column : template.getDataMapping().getDataValues()) {
                String seriesName = column.getHeader();
                if (seriesNames.contains(seriesName)) {
                    return seriesName;
                }
            }
        }
        // peek first in order
        return orderSeriesNames(seriesNames).getFirst();
    }

    private List<SeriesMeta> getSeriesMetas(@Nullable String templateName) {
        if (Strings.isNullOrEmpty(templateName)) {
            return Collections.emptyList();
        }

        Set<String> seriesNames = getSeriesNames(templateName);
        Map<String, Boolean> visibilitySettings = getVisibilitySettings(templateName, seriesNames);

        List<SeriesMeta> seriesMetas = new ArrayList<>(seriesNames.size());
        for (String seriesName : orderSeriesNames(seriesNames)) {
            boolean seriesVisible = visibilitySettings.getOrDefault(seriesName, false);
            seriesMetas.add(toSeriesMeta(templateName, seriesName, seriesVisible));
        }
        return seriesMetas;
    }

    private SeriesMeta toSeriesMeta(String templateName, String seriesName, boolean seriesVisible) {
        Check.notEmpty(templateName);
        Check.notEmpty(seriesName);

        return new SeriesMeta(
                seriesName,
                ColorPalette.highContrast().getColor(seriesName),
                createVisibleProperty(templateName, seriesName, seriesVisible));
    }

    private BooleanProperty createVisibleProperty(String templateName, String seriesName, boolean seriesVisible) {
        Check.notEmpty(templateName);
        Check.notEmpty(seriesName);

        BooleanProperty visibleProperty = new SimpleBooleanProperty(seriesVisible);
        visibleProperty.addListener((observable, oldValue, newValue) -> {
            showChart(templateName, seriesName, newValue);
            templateSettings.setSeriesVisible(templateName, seriesName, newValue);
        });

        return visibleProperty;
    }

    private ChangeListener<SeriesMeta> createSeriesChangeListener(String templateName) {
        Check.notEmpty(templateName);

        return (observable, oldValue, newValue) -> {
            if (Objects.equals(oldValue, newValue)) {
                return; // do nothing
            }
            String selectedSeriesName = null;
            if (newValue != null) {
                selectedSeriesName = newValue.name;
            }
            selectChart(templateName, selectedSeriesName);
            templateSettings.setSelectedSeriesName(templateName, selectedSeriesName);
            updateSeriesRemovalButton();
        };
    }

    // chart updates

    private void removeChart(SgyFile file, String seriesName) {
        Chart chart = model.getChart(file);
        if (chart instanceof SensorLineChart sensorChart) {
            sensorChart.removeFileColumn(seriesName);
        }
    }

    private void selectChart(String templateName, @Nullable String seriesName) {
        Check.notEmpty(templateName);

        for (SgyFile file : model.getFileManager().getFiles()) {
            if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                Chart chart = model.getChart(file);
                if (chart instanceof SensorLineChart sensorChart) {
                    sensorChart.selectChart(seriesName);
                }
            }
        }
    }

    private void showChart(String templateName, String seriesName, boolean show) {
        Check.notEmpty(templateName);
        Check.notEmpty(seriesName);

        for (SgyFile file : model.getFileManager().getFiles()) {
            if (Objects.equals(Templates.getTemplateName(file), templateName)) {
                Chart chart = model.getChart(file);
                if (chart instanceof SensorLineChart sensorChart) {
                    sensorChart.showChart(seriesName, show);
                }
            }
        }
    }

    // events

    private boolean reloadTemplateOnChange(SgyFile file) {
        if (Strings.isNullOrEmpty(selectedTemplateName)) {
            return false;
        }
        String templateName = Templates.getTemplateName(file);
        if (Objects.equals(templateName, selectedTemplateName)) {
            // reload template
            Platform.runLater(() -> selectTemplate(templateName));
            return true;
        }
        return false;
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        String templateName = Templates.getTemplateName(event.getFile());
        if (!hasOpenFiles(templateName)) {
            templateName = null;
        }
        if (!Objects.equals(templateName, selectedTemplateName)) {
            selectedTemplateName = templateName;
            String templateNameRef = templateName; // final reference
            Platform.runLater(() -> selectTemplate(templateNameRef));
        }
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        if (Strings.isNullOrEmpty(selectedTemplateName)) {
            return;
        }

        Set<File> openedFiles = new HashSet<>(event.getFiles());
        for (SgyFile file : model.getFileManager().getFiles()) {
            if (openedFiles.contains(file.getFile())) {
                if (reloadTemplateOnChange(file)) {
                    break;
                }
            }
        }
    }

	@EventListener
	private void onFileUpdated(FileUpdatedEvent event) {
        reloadTemplateOnChange(event.getFile());
	}

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        reloadTemplateOnChange(event.getFile());
    }

    @EventListener
    private void onSeriesUpdated(SeriesUpdatedEvent event) {
        String templateName = Templates.getTemplateName(event.getFile());
        if (!Objects.equals(templateName, selectedTemplateName)) {
            return;
        }
        String seriesName = event.getSeriesName();
        Platform.runLater(() -> {
            SeriesMeta series = getSeriesByName(seriesName);
            if (series == null) {
                selectTemplate(templateName);
                series = getSeriesByName(seriesName);
                if (series == null) {
                    return;
                }
            }
            series.visible.setValue(event.isSeriesVisible());
            showChart(templateName, seriesName, event.isSeriesVisible());
            if (event.isSeriesSelected()) {
                seriesSelector.setValue(series);
                selectChart(templateName, seriesName);
            }
        });
    }
}
