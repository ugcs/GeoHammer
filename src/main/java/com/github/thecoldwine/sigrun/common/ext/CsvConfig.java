package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CsvConfig {

    @Bean
    public FileTemplates fileTemplates(Status status) {
        return new FileTemplates(status);
    }

    @Bean
    public CsvFile csvFile(FileTemplates fileTemplates, PrefSettings prefSettings) {
        return new CsvFile(fileTemplates, prefSettings);
    }
}
