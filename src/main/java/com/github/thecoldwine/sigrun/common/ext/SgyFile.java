package com.github.thecoldwine.sigrun.common.ext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.quality.LineSchema;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.math.ScanProfile;
import com.ugcs.gprvisualizer.utils.Range;
import org.jspecify.annotations.Nullable;

public abstract class SgyFile {

	protected static double SPEED_SM_NS_VACUUM = 30.0;
	protected static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;

	@Nullable
	private File file;

	private boolean unsaved = true;

	@Nullable
    //horizontal cohesive lines of edges
    public List<HorizontalProfile> profiles;

	@Nullable
    private HorizontalProfile groundProfile;

	@Nullable
    // hyperbola probability calculated by AlgoritmicScan
    public ScanProfile algoScan;

	@Nullable
    // amplitude
    public ScanProfile amplScan;

	private List<BaseObject> auxElements = new ArrayList<>();
	
	private boolean spreadCoordinatesNecessary = false;

	@Nullable
	private PositionFile positionFile;

	public abstract List<GeoData> getGeoData();

	public SortedMap<Integer, Range> getLineRanges() {
		return LineSchema.getLineRanges(getGeoData());
	}

	public abstract int numTraces();

	public abstract void open(File file) throws Exception;
	
	public abstract void save(File file) throws IOException;
	
	public abstract void saveAux(File file) throws IOException;
	
	public abstract double getSamplesToCmGrn();

	public abstract double getSamplesToCmAir();

	public abstract SgyFile copy();

	public abstract int getSampleInterval();
	
	protected void write(BlockFile blockFile, FileChannel writechan, Block block)
			throws IOException {
		writechan.write(ByteBuffer.wrap(block.read(blockFile).array()));
	}

	@Nullable
	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public List<BaseObject> getAuxElements() {
		return auxElements;
	}

	public void setAuxElements(List<BaseObject> auxElements) {
		this.auxElements = auxElements;
	}

	public boolean isUnsaved() {
		return unsaved;
	}

	public void setUnsaved(boolean unsaved) {
		this.unsaved = unsaved;
	}

	public static double convertDegreeFraction(double org) {
		org = org / 100.0;
		int dgr = (int) org;
		double fract = org - dgr;
		double rx = dgr + fract / 60.0 * 100.0;
		return rx;
	}

	public static double convertBackDegreeFraction(double org) {
		
		int dgr = (int) org;
		double fr = org - dgr;
		double fr2 = fr * 60.0 / 100.0;
		double r = 100.0 * (dgr + fr2);
		
		return r;
	}

	public boolean isSpreadCoordinatesNecessary() {
		return spreadCoordinatesNecessary;
	}

	public void setSpreadCoordinatesNecessary(boolean spreadCoordinatesNecessary) {
		this.spreadCoordinatesNecessary = spreadCoordinatesNecessary;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(file);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (!(other instanceof SgyFile sgyFile)) {
			return false;
		}
        return Objects.equals(file, sgyFile.file);
	}

    public void setGroundProfileSource(PositionFile positionFile) {
		this.positionFile = positionFile;
    }

	public PositionFile getGroundProfileSource() {
		return positionFile;
	}

	public HorizontalProfile getGroundProfile() {
		return groundProfile;
	}

    public void setGroundProfile(HorizontalProfile groundProfile) {
		this.groundProfile = groundProfile;
    }
}
