package com.ugcs.geohammer.io.csv;

import com.ugcs.geohammer.util.Check;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class CsvWriter implements Closeable, Flushable {

	private final Writer writer;

	public CsvWriter(Writer writer) {
		Check.notNull(writer);

		this.writer = writer;
	}

	public void writeFields(List<String> fields) throws IOException {
		if (fields == null) {
			return;
		}
		for (int i = 0; i < fields.size(); ++i) {
			String field = fields.get(i);
			if (field != null) {
				boolean quote = field.indexOf(Csv.QUOTE_CHAR) != -1
						|| field.indexOf(Csv.SEPARATOR) != -1
						|| field.indexOf(Csv.LINE_END) != -1
						|| field.indexOf('\r') != -1;
				if (quote) {
					field = field.replace(
							String.valueOf(Csv.QUOTE_CHAR),
							Csv.ESCAPED_QUOTE_STRING);
				}
				if (quote) {
					writer.write(Csv.QUOTE_CHAR);
				}
				writer.write(field);
				if (quote) {
					writer.write(Csv.QUOTE_CHAR);
				}
			}
			if (i < fields.size() - 1) {
				writer.write(Csv.SEPARATOR);
			}
		}
		writer.write(Csv.LINE_END);
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}

	@Override
	public void flush() throws IOException {
		writer.flush();
	}
}
