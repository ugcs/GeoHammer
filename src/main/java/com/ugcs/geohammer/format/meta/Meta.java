package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Meta {

    private @Nullable IndexRange sampleRange;

    private @Nullable SpectrumType colorScale;

	private @Nullable Double contrast;

    private @Nullable Double maxGain;

	private @Nullable Boolean backgroundRemoved;

    private @Nullable IndexRange depthRange;

    // mark position indices in a local values list
    private Set<Integer> marks = new HashSet<>();

    private final ColumnSchema schema;

    private List<TraceGeoData> values = new ArrayList<>();

    public Meta(ColumnSchema schema) {
        this.schema = schema;
    }

    public static Meta ofSingleLine(ColumnSchema schema, int numValues) {
        Check.notNull(schema);
        Check.condition(numValues >= 0);

        Meta meta = new Meta(schema);
        meta.values = new ArrayList<>(numValues);
        // single line from 0 to numValues
        for (int i = 0; i < numValues; i++) {
            TraceGeoData value = new TraceGeoData(schema, i);
            value.setLine(0);
            meta.values.add(value);
        }
        return meta;
    }

    public static @Nullable Meta copy(@Nullable Meta meta) {
        if (meta == null) {
            return null;
        }
        return copy(meta, ColumnSchema.copy(meta.schema));
    }

    public static @Nullable Meta copy(@Nullable Meta meta, ColumnSchema schema) {
        if (meta == null) {
            return null;
        }

        Meta copy = new Meta(schema);
        copy.sampleRange = meta.sampleRange;
        copy.contrast = meta.contrast;
        copy.backgroundRemoved = meta.backgroundRemoved;
        copy.depthRange = meta.depthRange;
        copy.marks = new HashSet<>(meta.marks);
        copy.values = new ArrayList<>(meta.values.size());
        for (TraceGeoData value : meta.values) {
            copy.values.add(new TraceGeoData(copy.schema, value));
        }
        return copy;
    }

    public ColumnSchema getSchema() {
        return schema;
    }

    public @Nullable IndexRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(@Nullable IndexRange sampleRange) {
        this.sampleRange = sampleRange;
    }

    public @Nullable SpectrumType getColorScale() {
        return colorScale;
    }

    public void setColorScale(@Nullable SpectrumType colorScale) {
        this.colorScale = colorScale;
    }

    public @Nullable Double getContrast() {
        return contrast;
    }

    public void setContrast(@Nullable Double contrast) {
        this.contrast = contrast;
    }

    public @Nullable Double getMaxGain() {
        return maxGain;
    }

    public void setMaxGain(@Nullable Double maxGain) {
        this.maxGain = maxGain;
    }

    public @Nullable Boolean getBackgroundRemoved() {
        return backgroundRemoved;
    }

    public boolean isBackgroundRemoved() {
        return Boolean.TRUE.equals(backgroundRemoved);
    }

    public void setBackgroundRemoved(@Nullable Boolean backgroundRemoved) {
		this.backgroundRemoved = backgroundRemoved;
	}

    public @Nullable IndexRange getDepthRange() {
        return depthRange;
    }

    public void setDepthRange(@Nullable IndexRange depthRange) {
        this.depthRange = depthRange;
    }

    public Set<Integer> getMarks() {
        return marks;
    }

    public void setMarks(Set<Integer> marks) {
        this.marks = Check.notNull(marks);
    }

	public List<TraceGeoData> getValues() {
        return values;
    }

    public void setValues(List<TraceGeoData> values) {
        this.values = Check.notNull(values);
    }

    public int numValues() {
        return values.size();
    }

    // global trace index of the ith value
    public int getTraceIndex(int index) {
        TraceGeoData value = values.get(index);
        return value.getTraceIndex();
    }
}
