package com.ugcs.geohammer.view.status;

/**
 * Interface for status bar functionality.
 */
public interface Status {

	/**
	 * Shows a message in the status bar.
	 * 
	 * @param txt The message text
	 */
	void showProgressText(String txt);

	/**
	 * Shows a message in the status bar with a specified source.
	 * 
	 * @param message The message text
	 * @param source The source of the message
	 */
	void showMessage(String message, String source);
}
