package com.ugcs.gprvisualizer.app.parsers;

import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import com.ugcs.gprvisualizer.app.yaml.Template;


public abstract class BaseParsersTest {

    protected static final String basePath = "src/test/resources/";

    //protected static final String TestPassed = "Test Passed";
    //protected static final String TestFailed = "Test Failed";

    protected static final String YamlTestDataFolder = basePath + "TestData/Yaml/";
    protected static final String YamlCsvFolder = "CSV/";
    protected static final String YamlNmeaFolder = "Nmea/";
    protected static final String YamlMagdroneFolder = "MagDrone/";
    protected static final String YamlMagarrowFolder = "MagArrow/";
    protected static final String ColumnFixedWidthFolder = "ColumnFixedWidth/";
    protected static final String FTUTemplatesFolder = "./Mapping/FTUTemplates/";
    protected static final String PSFTemplatesFolder = "./Mapping/PSFTemplates/";
    protected static final String TemplatesFolder = "./Mapping/";
    protected static final String SegyTestDataFolder = "./TestData/Parsers/Segy/";
    protected static final String CSVTestDataFolder = basePath + "TestData/Parsers/CSV/";
    protected static final String FCWTestDataFolder = basePath + "TestData/Parsers/FixedColumnWidth/";

    private Constructor c = new Constructor(Template.class, new LoaderOptions()); 

    {
        c.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<? extends Object> type, String name) {
                if (name.indexOf('-') > -1) {
                    name = toCameCase(name);
                }
                return super.getProperty(type, name);
            }

            private String toCameCase(String name) {
                String[] parts = name.split("-");
                StringBuilder sb = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    sb.append(StringUtils.capitalize(parts[i]));
                }
                return sb.toString();
            }
        });
    }

    protected Yaml deserializer = new Yaml(c);
}