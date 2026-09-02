package com.ugcs.geohammer.format.svlog;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.IndexRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarFileLineExportTest {

	private static final int NMEA_WRAPPER_ID = 109;

	private static final int ATOF_POINT_DATA_ID = 3012;

	private static final double SPEED_OF_SOUND = 1500.0;

	@TempDir
	Path tempDir;

	@Test
	void exportsPacketsOfTheSelectedLineOnly() throws Exception {
		SonarFile source = new SonarFile();
		source.open(writeSurvey("survey.svlog", 30).toFile());

		// a trace is kept for every packet, so 30 pings produce 60 traces
		List<GeoData> values = source.getGeoData();
		assertEquals(60, values.size());
		for (int i = 0; i < values.size(); i++) {
			values.get(i).setLine(i / 20);
		}
		source.tracesChanged();

		IndexRange secondLine = source.getLineRanges().get(1);
		assertEquals(new IndexRange(20, 40), secondLine);

		Path exported = tempDir.resolve("line1.svlog");
		source.save(exported.toFile(), secondLine);

		SonarFile line = new SonarFile();
		line.open(exported.toFile());

		List<GeoData> exportedValues = line.getGeoData();
		assertEquals(secondLine.size(), exportedValues.size());
		// the exported line starts on a position packet, so its depth is only known
		// from the first sonar ping of the line onwards
		for (int i = 1; i < exportedValues.size(); i++) {
			Number expected = values.get(secondLine.from() + i).getNumber(SonarSchema.DEPTH_HEADER);
			Number actual = exportedValues.get(i).getNumber(SonarSchema.DEPTH_HEADER);
			assertTrue(expected != null && actual != null, "depth is missing at " + i);
			assertEquals(expected.doubleValue(), actual.doubleValue(), 0.01);
		}
		Number firstDepth = exportedValues.get(1).getNumber(SonarSchema.DEPTH_HEADER);
		assertEquals(depthAt(10), firstDepth.doubleValue(), 0.01);
	}

	private static double depthAt(int pingIndex) {
		return 5.0 + 0.1 * pingIndex;
	}

	private Path writeSurvey(String name, int numPings) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = 0; i < numPings; i++) {
			out.write(packet(NMEA_WRAPPER_ID, gga(57.0 + i * 1e-5, 24.0)));
			out.write(packet(ATOF_POINT_DATA_ID, atofPoint(depthAt(i))));
		}
		Path file = tempDir.resolve(name);
		Files.write(file, out.toByteArray());
		return file;
	}

	private static byte[] gga(double latitude, double longitude) {
		String sentence = String.format(Locale.US,
				"$GNGGA,120000.00,%02d%08.5f,N,%03d%08.5f,E,1,10,0.8,0.0,M,0.0,M,,*00",
				(int) latitude, (latitude - (int) latitude) * 60.0,
				(int) longitude, (longitude - (int) longitude) * 60.0);
		return sentence.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] atofPoint(double depth) {
		ByteBuffer payload = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
		payload.putFloat(16, (float) SPEED_OF_SOUND);
		payload.putShort(36, (short) 1);
		payload.putFloat(40, 0.0f);
		payload.putFloat(44, (float) (2.0 * depth / SPEED_OF_SOUND));
		return payload.array();
	}

	private static byte[] packet(int packetId, byte[] payload) {
		ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length + 2).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put((byte) 'B');
		buffer.put((byte) 'R');
		buffer.putShort((short) payload.length);
		buffer.putShort((short) packetId);
		buffer.put((byte) 0);
		buffer.put((byte) 0);
		buffer.put(payload);

		int checksum = 0;
		for (int i = 0; i < 8 + payload.length; i++) {
			checksum += buffer.get(i) & 0xff;
		}
		buffer.putShort((short) (checksum & 0xffff));
		return buffer.array();
	}
}
