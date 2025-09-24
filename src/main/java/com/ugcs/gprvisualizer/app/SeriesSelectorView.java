package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.TemplateSettings;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Views;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class SeriesSelectorView extends VBox implements InitializingBean {

    private static final String DEFAULT_TITLE = "Select chart";

    private final Model model;

    private final TemplateSettings templateSettings;

    @Nullable
    private CsvFile selectedFile;

    @Nullable
    private volatile Template selectedTemplate;

    private Label title;

    private ObservableList<SeriesMeta> series = FXCollections.observableArrayList();

    private ComboBox<SeriesMeta> seriesSelector;

    private ChangeListener<SeriesMeta> seriesChangeListener;

    public record SeriesMeta(String name, Color color, BooleanProperty visible) {

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

        title = new Label(DEFAULT_TITLE);
        title.setStyle("-fx-text-fill: #dddddd;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        container.getChildren().addAll(title, spacer, seriesSelector);
        getChildren().addAll(container);
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
                    setStyle("-fx-text-fill: " + Views.toColorString(item.color()) + ";");
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
                    setStyle("-fx-text-fill: " + Views.toColorString(item.color()) + ";");
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
            if (selectedSeries == null && !series.isEmpty()) {
                // select first item in the list
                selectedSeries = series.getFirst();
            }

            if (selectedSeries != null) {
                seriesSelector.setValue(selectedSeries);
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
            if (templateEquals(csvFile.getTemplate(), template)) {
                return true;
            }
        }
        return false;
    }

    private boolean templateEquals(@Nullable Template a, @Nullable Template b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getName(), b.getName());
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
            if (templateEquals(csvFile.getTemplate(), template)) {
                model.getCsvChart(csvFile).ifPresent(chart
                        -> seriesNames.addAll(chart.getSeriesNames()));
            }
        }
        return seriesNames;
    }

    private List<String> orderSeriesNames(@Nullable Template template, Set<String> seriesNames) {
        List<String> orderedSeriesNames = new ArrayList<>();
        // put template columns in declaration order
        if (template != null) {
            DataMapping dataMapping = template.getDataMapping();
            for (SensorData sensorData : Nulls.toEmpty(dataMapping.getDataValues())) {
                String seriesName = sensorData.getSemantic();
                if (seriesNames.remove(seriesName)) {
                    orderedSeriesNames.add(seriesName);
                }
            }
        }
        // sort undeclared columns alphabetically
        List<String> undeclaredSeriesNames = new ArrayList<>(seriesNames);
        Collections.sort(undeclaredSeriesNames);
        orderedSeriesNames.addAll(undeclaredSeriesNames);
        return orderedSeriesNames;
    }

    private List<SeriesMeta> getSeriesMetas(@Nullable Template template) {
        if (template == null) {
            return Collections.emptyList();
        }

        List<String> seriesNames = orderSeriesNames(template, getSeriesNames(template));
        List<SeriesMeta> seriesMetas = new ArrayList<>(seriesNames.size());
        for (String seriesName : seriesNames) {
            seriesMetas.add(toSeriesMeta(template, seriesName));
        }
        return seriesMetas;
    }

    private SeriesMeta toSeriesMeta(Template template, String seriesName) {
        Check.notNull(template);
        Check.notEmpty(seriesName);

        return new SeriesMeta(
                seriesName,
                model.getColorBySemantic(seriesName),
                createVisibleProperty(template, seriesName));
    }

    private BooleanProperty createVisibleProperty(Template template, String seriesName) {
        Check.notNull(template);
        Check.notEmpty(seriesName);

        boolean visible = templateSettings.isSeriesVisible(template, seriesName);
        BooleanProperty visibleProperty = new SimpleBooleanProperty(visible);

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
        };
    }

    // chart updates

    private void selectChart(Template template, @Nullable String seriesName) {
        Check.notNull(template);

        for (CsvFile csvFile : model.getFileManager().getCsvFiles()) {
            if (templateEquals(csvFile.getTemplate(), template)) {
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
            if (templateEquals(csvFile.getTemplate(), template)) {
                SensorLineChart chart = model.getCsvChart(csvFile).orElse(null);
                if (chart != null) {
                    chart.showChart(seriesName, show);
                }
            }
        }
    }

    // events

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Template template = null;
        if (event.getFile() instanceof CsvFile csvFile) {
            template = csvFile.getTemplate();
            if (!hasOpenFiles(template)) {
                template = null;
            }
        }
        if (!templateEquals(template, selectedTemplate)) {
            selectedTemplate = template;
            selectTemplate(template);
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
                Template template = csvFile.getTemplate();
                if (templateEquals(template, selectedTemplate)) {
                    // reload template
                    selectTemplate(template);
                    break;
                }
            }
        }
    }

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        if (selectedTemplate == null) {
            return;
        }

        if (event.getSgyFile() instanceof CsvFile csvFile) {
            Template template = csvFile.getTemplate();
            if (templateEquals(template, selectedTemplate)) {
                // reload template
                selectTemplate(template);
            }
        }
    }
}
