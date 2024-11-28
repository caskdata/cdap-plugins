/*
 * Copyright © 2022 Cask Data, Inc.
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

package io.cdap.plugin.format;

import com.google.gson.stream.JsonWriter;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.format.io.BigDecimalAwareJsonStructuredRecordDatumWriter;
import io.cdap.cdap.format.io.BigDecimalAwareJsonWriter;
import io.cdap.cdap.format.io.JsonEncoder;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Structured record converted that handles writing decimal files as numbers in the output (instead of int arrays).
 */
public class BigDecimalAwareStructuredRecordStringConverter {
  private static final BigDecimalAwareJsonStructuredRecordDatumWriter JSON_DATUM_WRITER =
    new BigDecimalAwareJsonStructuredRecordDatumWriter();

  /**
   * Converts a {@link StructuredRecord} to a json string.
   */
  public static String toJsonString(StructuredRecord record) throws IOException {
    StringWriter strWriter = new StringWriter();
    try (JsonWriter writer = new BigDecimalAwareJsonWriter(strWriter)) {
      JSON_DATUM_WRITER.encode(record, new JsonEncoder(writer));
      return strWriter.toString();
    }
  }

  /**
   * Converts a {@link StructuredRecord} to a delimited string.
   */
  public static String toDelimitedString(final StructuredRecord record, String delimiter) {
    return record.getSchema().getFields().stream()
      .map(f -> mapField(record, f))
      .collect(Collectors.joining(delimiter));
  }

  /**
   * Get the string representation for a given record field. BigDecimals are printed as plain strings.
   * @param record record to process
   * @param field field to extract
   * @return String representing the value for this field.
   */
  private static String mapField(StructuredRecord record, Schema.Field field) {
    String fieldName = field.getName();
    Object value = record.get(fieldName);

    // Return null value as empty string.
    if (value == null) {
      return "";
    }

    // Get the field schema.
    Schema fieldSchema = field.getSchema();
    if (fieldSchema.isNullable()) {
      fieldSchema = fieldSchema.getNonNullable();
    }

    // Write decimal values as decimal strings.
    if (fieldSchema.getLogicalType() != null && fieldSchema.getLogicalType() == Schema.LogicalType.DECIMAL) {
      BigDecimal decimalValue = record.getDecimal(fieldName);

      // Throw exception if the field is expected tu be decimal, but it could not be processed as such.
      if (decimalValue == null) {
        throw new IllegalArgumentException("Invalid schema for field " + fieldName + ". Decimal was expected.");
      }
      return decimalValue.toPlainString();
    }

    return value.toString();
  }

  private BigDecimalAwareStructuredRecordStringConverter() {
    //inaccessible constructor for static class
  }
}
