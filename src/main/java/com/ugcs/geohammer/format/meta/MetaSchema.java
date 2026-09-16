package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;

public final class MetaSchema {

	public static ColumnSchema createSchema() {
		ColumnSchema schema = new ColumnSchema();
		// line
		schema.addColumn(new Column(Semantic.LINE.getName())
				.withSemantic(Semantic.LINE.getName())
				.withDisplay(true)
				.withReadOnly(true));
		schema.addColumn(new Column(Semantic.LATITUDE.getName())
				.withSemantic(Semantic.LATITUDE.getName())
				.withDisplay(false)
				.withReadOnly(true));
		schema.addColumn(new Column(Semantic.LONGITUDE.getName())
				.withSemantic(Semantic.LONGITUDE.getName())
				.withDisplay(false)
				.withReadOnly(true));
		return schema;
	}

	private MetaSchema() {
	}
}
