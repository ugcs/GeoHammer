package com.ugcs.geohammer.view.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a message to be displayed in the status bar.
 * Includes timestamp and source information.
 */
public class StatusMessage {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final String message;
    private final String source;
    private final LocalDateTime timestamp;
    
    /**
     * Creates a new status message.
     * 
     * @param message The message text
     * @param source The source of the message
     */
    public StatusMessage(String message, String source) {
        this.message = message;
        this.source = source;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Gets the message text.
     * 
     * @return The message text
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Gets the source of the message.
     * 
     * @return The source
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Gets the timestamp of when the message was created.
     * 
     * @return The timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets a formatted string representation of the timestamp.
     * 
     * @return The formatted timestamp
     */
    public String getFormattedTimestamp() {
        return timestamp.format(FORMATTER);
    }
    
    /**
     * Gets a formatted string representation of the message including timestamp and source.
     * 
     * @return The formatted message
     */
    public String getFormattedMessage() {
        return String.format("[%s] [%s] %s", getFormattedTimestamp(), source, message);
    }
    
    /**
     * Gets a short formatted string representation of the message for display in the status bar.
     * 
     * @return The short formatted message
     */
    public String getShortFormattedMessage() {
        return String.format("[%s] %s", source, message);
    }
    
    @Override
    public String toString() {
        return getFormattedMessage();
    }
}