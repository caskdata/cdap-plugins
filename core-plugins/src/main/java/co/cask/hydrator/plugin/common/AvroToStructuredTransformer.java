/*
 * Copyright © 2015-2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.hydrator.plugin.common;

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.hydrator.common.RecordConverter;
import com.google.common.collect.Maps;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Create StructuredRecords from GenericRecords
 */
public class AvroToStructuredTransformer extends RecordConverter<GenericRecord, StructuredRecord> {

  private final Map<Integer, Schema> schemaCache = Maps.newHashMap();

  public AvroToStructuredTransformer() {
    super(false, false);
  }

  public AvroToStructuredTransformer(boolean convertMicrosToMillis, boolean convertTimestampToMicros) {
    super(convertMicrosToMillis, convertTimestampToMicros);
  }

  public StructuredRecord transform(GenericRecord genericRecord) throws IOException {
    org.apache.avro.Schema genericRecordSchema = genericRecord.getSchema();
    return transform(genericRecord, convertSchema(genericRecordSchema));
  }

  @Override
  public StructuredRecord transform(GenericRecord genericRecord, Schema structuredSchema) throws IOException {
    Schema modifiedSchema = structuredSchema;
    // Hack: AvroSerDe writes timestamp-micros as timestamp-millis. So convert schema with timestamp-millis to
    // timestamp-micros.
    if (convertTimestampToMicros) {
      modifiedSchema = convertToMicros(structuredSchema);
    }
    StructuredRecord.Builder builder = StructuredRecord.builder(modifiedSchema);
    for (Schema.Field field : structuredSchema.getFields()) {
      String fieldName = field.getName();
      builder.set(fieldName, convertField(genericRecord.get(fieldName), field.getSchema()));
    }
    return builder.build();
  }

  public StructuredRecord.Builder transform(GenericRecord genericRecord, Schema structuredSchema,
                                            @Nullable String skipField) throws IOException {
    StructuredRecord.Builder builder = StructuredRecord.builder(structuredSchema);
    for (Schema.Field field : structuredSchema.getFields()) {
      String fieldName = field.getName();
      if (!fieldName.equals(skipField)) {
        builder.set(fieldName, convertField(genericRecord.get(fieldName), field.getSchema()));
      }
    }

    return builder;
  }

  public Schema convertSchema(org.apache.avro.Schema schema) throws IOException {
    int hashCode = schema.hashCode();
    Schema structuredSchema;

    if (schemaCache.containsKey(hashCode)) {
      structuredSchema = schemaCache.get(hashCode);
    } else {
      structuredSchema = Schema.parseJson(schema.toString());
      schemaCache.put(hashCode, structuredSchema);
    }
    return structuredSchema;
  }

  private Schema convertToMicros(Schema schema) {
    if (convertTimestampToMicros && schema.getLogicalType() == Schema.LogicalType.TIMESTAMP_MILLIS) {
      return Schema.of(Schema.LogicalType.TIMESTAMP_MICROS);
    }

    if (schema.getType() == Schema.Type.UNION) {
      List<Schema> schemas = new ArrayList<>();
      for (Schema.Field field : schema.getFields()) {
        schemas.add(convertToMicros(field.getSchema()));
      }
      return Schema.unionOf(schemas);
    }

    return schema;
  }
}
