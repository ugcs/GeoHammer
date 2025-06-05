package com.ugcs.gprvisualizer.csv;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.opencsv.bean.CsvBindByName;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;

public class DeclaredColumnOrder implements Comparator<String> {

    private final List<String> columns = new ArrayList<>();

    public <T> DeclaredColumnOrder(Class<T> type) {
        Check.notNull(type);

        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            String column = null;
            CsvBindByName bindByName = field.getDeclaredAnnotation(CsvBindByName.class);
            if (bindByName != null) {
                column = bindByName.column();
            }
            if (Strings.isNullOrEmpty(column)) {
                column = field.getName();
            }
            columns.add(Nulls.toEmpty(column).toLowerCase(Locale.US));
        }
    }

    @Override
    public int compare(String column1, String column2) {
        int index1 = columns.indexOf(Nulls.toEmpty(column1).toLowerCase(Locale.US));
        int index2 = columns.indexOf(Nulls.toEmpty(column2).toLowerCase(Locale.US));
        return Integer.compare(index1, index2);
    }
}
