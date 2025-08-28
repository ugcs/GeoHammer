package com.ugcs.gprvisualizer.app.axis;

import com.github.thecoldwine.sigrun.common.ext.CsvConfig;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(CsvConfig.class)
public class AxisConfig {

    @Bean
    public SensorLineChartXAxis sensorLineChartXAxis(CsvFile file) {
        return new SensorLineChartXAxis(10, file);
    }
}
