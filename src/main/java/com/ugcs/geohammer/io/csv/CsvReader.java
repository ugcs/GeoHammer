package com.ugcs.geohammer.io.csv;

import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// RFC 4180
public class CsvReader implements Closeable {

	private final Reader reader;

	private final char[] buffer;

	private int length;

	private int offset;

	private char[] fieldBuffer = new char[1_024];

	public CsvReader(Reader reader) {
		this(reader, 65_536);
	}

	public CsvReader(Reader reader, int bufferSize) {
		Check.notNull(reader);
		Check.condition(bufferSize > 0);

		this.reader = reader;
		this.buffer = new char[bufferSize];
		this.length = 0;
		this.offset = 0;
	}

	private char readChar() throws IOException {
		if (offset == length) {
			// re-fill buffer
			length = reader.read(buffer, 0, buffer.length);
			offset = 0;
		}
		if (length == -1) {
			return 0;
		}
		return buffer[offset++];
	}

	public @Nullable List<String> readFields() throws IOException {
		if (length == -1) {
			return null;
		}

		List<String> result = new ArrayList<>();
		int fieldLength = 0;

		boolean quoted = false;
		boolean consumed = false;
		while (length != -1) {
			char c = readChar();
			if (length == -1) {
				break;
			}
			consumed = true;
			if (!quoted) {
				if (c == 0x0d) {
					// skip LF, if necessary
					char c1 = readChar();
					if (length == -1) {
						break;
					}
					if (c1 != 0x0a) {
						offset -= 1;
					}
					break;
				}
				if (c == 0x0a) {
					break;
				}
				if (c == Csv.SEPARATOR) {
					// next field
					result.add(new String(fieldBuffer, 0, fieldLength));
					fieldLength = 0;
					continue;
				}
				if (c == Csv.QUOTE_CHAR) {
					quoted = true;
					continue;
				}
			} else {
				if (c == Csv.QUOTE_CHAR) {
					// check if quote is escaped
					char c1 = readChar();
					if (length == -1) {
						break;
					}
					if (c1 != Csv.QUOTE_CHAR) {
						quoted = false;
						offset -= 1;
						continue;
					}
				}
			}
			if (fieldLength + 1 >= fieldBuffer.length) {
				fieldBuffer = Arrays.copyOf(fieldBuffer,
						Math.max(fieldBuffer.length + (fieldBuffer.length >> 1), fieldLength + 1));
			}
			fieldBuffer[fieldLength++] = c;
		}
		if (!consumed) {
			// end of input right after the last line break
			return null;
		}
		result.add(new String(fieldBuffer, 0, fieldLength));
		return result;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}
