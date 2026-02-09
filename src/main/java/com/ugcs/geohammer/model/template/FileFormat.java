package com.ugcs.geohammer.model.template;

import java.util.List;

import org.jspecify.annotations.NullUnmarked;
import org.springframework.util.StringUtils;

import com.ugcs.geohammer.model.template.data.Date;


/**
 * The FileFormat class represents the format of a file, including its properties such as comment prefix,
 * separator, decimal separator, header presence, and column lengths.
 */
@NullUnmarked
public class FileFormat {

    private String commentPrefix;

    private String separator;

    private boolean repeatableSeparator = false;

    private String decimalSeparator;

    private boolean hasHeader = false;

    private List<Short> columnLengths;

    /**
     * Checks if the format of the file is valid.
     * @param type The type of the file.
     * @param columns The columns of the file.
     * @return True if the format is valid, false otherwise.
     */
    public boolean isFormatValid(Template.FileType type, DataMapping columns) {
        return switch (type) {
            case CSV -> isDecimalSeparatorValid() && separator != null && commentPrefix != null
                    && columns != null && isDateTimeColumnsValid(columns)
                    && (hasHeader ? (columns.getLatitude() != null 
                            && StringUtils.hasLength(columns.getLatitude().getHeader())
                            && columns.getLongitude() != null 
                            && StringUtils.hasLength(columns.getLongitude().getHeader())
                            &&  columns.getLatitude().getIndex() == null
                            &&  columns.getLongitude().getIndex() == null)
                            : (columns.getLatitude() != null && columns.getLatitude().getIndex() != null
                                    && columns.getLongitude() != null && columns.getLongitude().getIndex() != null)
                                    && !StringUtils.hasLength(columns.getLongitude().getHeader()));

            case ColumnsFixedWidth -> isDecimalSeparatorValid() && columnLengths != null && commentPrefix != null
                    && columns != null
                    && columns.getLatitude() != null && columns.getLatitude().getIndex() != null
                    && columns.getLongitude() != null && columns.getLongitude().getIndex() != null;

            default -> false;
        };
    }
    
    /**
     * Checks if the date and time columns are valid.
     * @param columns The columns of the file.
     * @return true if the date and time columns are valid, false otherwise.
     */
    private boolean isDateTimeColumnsValid(DataMapping columns) {
        return (columns.getDate() != null && StringUtils.hasLength(columns.getDate().getFormat())
                && columns.getTime() != null && StringUtils.hasLength(columns.getTime().getFormat())
                && ((Date.Source.FileName.equals(columns.getDate().getSource())
                        && !(!StringUtils.hasLength(columns.getTime().getHeader())
                                && columns.getTime().getIndex() == null))
                        || (Date.Source.FileName.equals(columns.getDate().getSource())
                                && !StringUtils.hasLength(columns.getTime().getHeader())
                                && columns.getTime().getIndex() != null)))
                || (!(columns.getDateTime() != null 
                        && !StringUtils.hasLength(columns.getDateTime().getHeader())
                        && columns.getDateTime().getIndex() == null))
                || (!StringUtils.hasLength(columns.getDateTime().getHeader())
                        && columns.getDateTime().getIndex() != null
                        && StringUtils.hasLength(columns.getDateTime().getFormat()))
                || (((!(!StringUtils.hasLength(columns.getTime().getHeader())
                        && columns.getTime().getIndex() == null))
                        && StringUtils.hasLength(columns.getDate().getHeader())
                        && columns.getDate().getIndex() == null)
                        || (!StringUtils.hasLength(columns.getTime().getHeader())
                                && columns.getTime().getIndex() != null)
                                && (!StringUtils.hasLength(columns.getDate().getHeader())
                                        && columns.getDate().getIndex() != null)
                                && StringUtils.hasLength(columns.getDate().getFormat())
                                && StringUtils.hasLength(columns.getTime().getFormat()));
    } 

    private boolean isDecimalSeparatorValid() {
        return ".".equals(decimalSeparator) || ",".equals(decimalSeparator);
    }

    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public String getCommentPrefix() {
        return commentPrefix;
    }

    public void setCommentPrefix(String commentPrefix) {
        this.commentPrefix = commentPrefix;
    }

    public String getDecimalSeparator() {
        return decimalSeparator;
    }

    public void setDecimalSeparator(String decimalSeparator) {
        this.decimalSeparator = decimalSeparator;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public boolean isRepeatableSeparator() {
        return repeatableSeparator;
    }

    public void setRepeatableSeparator(boolean repeatableSeparator) {
        this.repeatableSeparator = repeatableSeparator;
    }

    public List<Short> getColumnLengths() {
        return columnLengths;
    }

    public void setColumnLengths(List<Short> columnLengths) {
        this.columnLengths = columnLengths;
    }
}