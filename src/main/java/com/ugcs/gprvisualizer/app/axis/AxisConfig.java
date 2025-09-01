package com.ugcs.gprvisualizer.app.axis;

import com.github.thecoldwine.sigrun.common.ext.CsvConfig;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.service.TemplateUnitService;
import com.ugcs.gprvisualizer.gpr.Model;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(CsvConfig.class)
public class AxisConfig {

    @Bean
    public SensorLineChartXAxis sensorLineChartXAxis(Model model, ApplicationEventPublisher eventPublisher, TemplateUnitService templateUnitService, CsvFile file) {
        return new SensorLineChartXAxis(model, templateUnitService, 10, file);
    }
}
