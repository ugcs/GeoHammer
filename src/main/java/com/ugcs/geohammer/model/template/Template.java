package com.ugcs.geohammer.model.template;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.NullUnmarked;
import org.springframework.util.StringUtils;

/**
 * Represents a template for processing files.
 */
@NullUnmarked
public class Template {

    private String name;

    private String code;

    private FileType fileType = FileType.Unknown;

    private String matchRegex;

    private FileFormat fileFormat;

    private String dataValidation;

    private DataMapping dataMapping;

    private SkipLinesTo skipLinesTo;

    private boolean reorderByTime = false;

	private boolean hasPositionsSource = false;

    /**
     * Initialize template on load
     */
    public void init() {
        addLineDataValue();
        addMarkDataValue();

        initIndexedHeaders();
    }

    private void addLineDataValue() {
        if (dataMapping == null) {
            return;
        }
        String lineSemantic = Semantic.LINE.getName();
        SensorData line = dataMapping.getDataValueBySemantic(lineSemantic);
        if (line == null) {
            line = new SensorData();
            line.setSemantic(lineSemantic);
            line.setHeader(lineSemantic);
            line.setReadOnly(true);
            dataMapping.addDataValue(line);
        }
    }

    private void addMarkDataValue() {
        if (dataMapping == null) {
            return;
        }
        String markSemantic = Semantic.MARK.getName();
        SensorData mark = dataMapping.getDataValueBySemantic(markSemantic);
        if (mark == null) {
            mark = new SensorData();
            mark.setSemantic(markSemantic);
            mark.setHeader(markSemantic);
            mark.setReadOnly(true);
            dataMapping.addDataValue(mark);
        }
    }

    private void initIndexedHeaders() {
        if (dataMapping == null) {
            return;
        }
        List<BaseData> dataValues = new ArrayList<>();
        dataValues.addAll(dataMapping.getMetaValues());
        dataValues.addAll(Nulls.toEmpty(dataMapping.getDataValues()));
        for (BaseData dataValue : dataValues) {
            if (dataValue.getIndex() != null
                    && Strings.isNullOrBlank(dataValue.getHeader())) {
                dataValue.setHeader("column_" + dataValue.getIndex());
            }
        }
    }

    /**
     * Checks if the template is valid.
     *
     * @return true if the template is valid, false otherwise.
     */
    public boolean isTemplateValid() {
        return isValidRegex()
                && StringUtils.hasLength(name)
                && StringUtils.hasLength(code)
                && !FileType.Unknown.equals(fileType)
                && isFormatValid();
    }

    /**
     * Checks if the match regex is valid.
     *
     * @return true if the match regex is valid, false otherwise.
     */
    private boolean isValidRegex() {
        if (!StringUtils.hasLength(matchRegex))
            return false;
        try {
            Pattern.compile(matchRegex);
        } catch (PatternSyntaxException ex) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the file format is valid.
     *
     * @return true if the file format is valid, false otherwise.
     */
    private boolean isFormatValid() {
        return fileFormat != null && fileFormat.isFormatValid(fileType, dataMapping);
    }

    // Getters and Setters for name, code, fileType, matchRegex, fileFormat

    /**
     * Gets the name of the template.
     *
     * @return the name of the template.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the template.
     *
     * @param name the name of the template.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the code of the template.
     *
     * @return the code of the template.
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the code of the template.
     *
     * @param code the code of the template.
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Gets the file type of the template.
     *
     * @return the file type of the template.
     */
    public FileType getFileType() {
        return fileType;
    }

    /**
     * Sets the file type of the template.
     *
     * @param fileType the file type of the template.
     */
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Gets the match regex of the template.
     *
     * @return the match regex of the template.
     */
    public String getMatchRegex() {
        return matchRegex;
    }

    /**
     * Sets the match regex of the template.
     *
     * @param matchRegex the match regex of the template.
     */
    public void setMatchRegex(String matchRegex) {
        this.matchRegex = matchRegex;
    }

    /**
     * Gets the file format of the template.
     *
     * @return the file format of the template.
     */
    public FileFormat getFileFormat() {
        return fileFormat;
    }

    /**
     * Sets the file format of the template.
     *
     * @param fileFormat the file format of the template.
     */
    public void setFileFormat(FileFormat fileFormat) {
        this.fileFormat = fileFormat;
    }

    /**
     * Get data validation expression of the data values.
     */
    public String getDataValidation() {
        return dataValidation;
    }

    /**
     * Set data validation expression of the data values.
     *
     * @param dataValidation the data validation expression.
     */
    public void setDataValidation(String dataValidation) {
        this.dataValidation = dataValidation;
    }

    // Getters and Setters for dataMapping, skipLinesTo

    /**
     * Gets the data mapping of the template.
     *
     * @return the data mapping of the template.
     */
    public DataMapping getDataMapping() {
        return dataMapping;
    }

    /**
     * Sets the data mapping of the template.
     *
     * @param dataMapping the data mapping of the template.
     */
    public void setDataMapping(DataMapping dataMapping) {
        this.dataMapping = dataMapping;
    }

    /**
     * Gets the skip lines to of the template.
     *
     * @return the skip lines to of the template.
     */
    public SkipLinesTo getSkipLinesTo() {
        return skipLinesTo;
    }

    /**
     * Sets the skip lines to of the template.
     *
     * @param skipLinesTo the skip lines to of the template.
     */
    public void setSkipLinesTo(SkipLinesTo skipLinesTo) {
        this.skipLinesTo = skipLinesTo;
    }

    public boolean isReorderByTime() {
        return reorderByTime;
    }

    public void setReorderByTime(boolean reorderByTime) {
        this.reorderByTime = reorderByTime;
    }

	public boolean isHasPositionsSource() {
		return hasPositionsSource;
	}

	public void setHasPositionsSource(boolean isSource) {
		this.hasPositionsSource = isSource;
	}

	/**
     * Returns a string representation of the template.
     *
     * @return a string representation of the template.
     */
    @Override
    public String toString() {
        return "Template{" +
                "name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", fileType=" + fileType +
				", hasPositionsSource=" + hasPositionsSource +
                ", matchRegex='" + matchRegex + '\'' +
                ", fileFormat=" + fileFormat +
                ", dataValidation=" + dataValidation +
                ", dataMapping=" + dataMapping +
                '}';
    }

    /**
     * Represents the file types supported by the template.
     */
    public enum FileType {
        CSV,
        ColumnsFixedWidth,
        Segy,
        Unknown
    }
}