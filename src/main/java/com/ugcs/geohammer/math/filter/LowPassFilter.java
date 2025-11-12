package com.ugcs.geohammer.math.filter;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;

import java.util.ArrayList;
import java.util.List;

public class LowPassFilter implements SequenceFilter {

    private final int order;

    public LowPassFilter(int order) {
        Check.condition(order >= 0);
        this.order = order;
    }

    @Override
    public List<Number> apply(List<Number> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return values;
        }

        // data size should be >= 2 * order + 1
        // min order = (size - 1) / 2
        int alignedOrder = Math.min(order, (values.size() - 1) / 2);
        if (alignedOrder == 0) {
            return values;
        }

        int shift = alignedOrder / 2;
        FirFilter filter = new FirFilter(alignedOrder);

        var filtered = filter.filterList(values).subList(shift, values.size() + shift);
        assert filtered.size() == values.size();

        List<Number> valuesNonNull = new ArrayList<>();
        List<Number> filteredValuesNonNull = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Number value = values.get(i);
            Number filteredValue = filtered.get(i);
            if (value != null && filteredValue != null) {
                valuesNonNull.add(value);
                filteredValuesNonNull.add(filteredValue);
            }
        }

        double rmsOriginal = calculateRms(valuesNonNull);
        double rmsFiltered = calculateRms(filteredValuesNonNull);

        double scale = rmsOriginal != 0.0 && rmsFiltered != 0.0
                ? rmsOriginal / rmsFiltered
                : 0.0;
        for (int i = 0; i < filtered.size(); i++) {
            Number value = filtered.get(i);
            if (value != null) {
                filtered.set(i, scale * value.doubleValue());
            }
        }
        return filtered;
    }

    private static double calculateRms(List<Number> values) {
        double sum = 0.0;
        int count = 0;
        for (Number value : values) {
            if (value == null) {
                continue;
            }
            sum += value.doubleValue() * value.doubleValue();
            count++;
        }
        return count > 0 ? Math.sqrt(sum / count) : 0.0;
    }
}
