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

package io.cdap.cdap.format.io;

import com.google.gson.stream.JsonWriter;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.common.io.Encoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Custom Datum Writer for Structured Records, this class writes BigDecimal values as numbers instead of byte arrays in
 * the JSON output.
 */
public class BigDecimalAwareJsonStructuredRecordDatumWriter extends JsonStructuredRecordDatumWriter {
  @Override
  protected void encode(Encoder encoder, Schema schema, Object value) throws IOException {
    Schema nonNullableSchema = schema.isNullable() ? schema.getNonNullable() : schema;
    Schema.LogicalType logicalType = nonNullableSchema.getLogicalType();

    if (value != null && logicalType == Schema.LogicalType.DECIMAL) {
      BigDecimal bdValue = fromObject(value, nonNullableSchema);
      getJsonWriter(encoder).value(bdValue);
      return;
    }

    super.encode(encoder, schema, value);
  }

  /**
   * Extract a BigDecimal value from a supplied object
   * @param value object to convert into BigDecimal
   * @param logicalTypeSchema logical type schema for this field
   * @return value converted ingo a BigDecimal.
   */
  protected BigDecimal fromObject(Object value, Schema logicalTypeSchema) {
    // Return BigDecimal as is
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }

    // Handle the value as a byte buffer
    int scale = logicalTypeSchema.getScale();
    if (value instanceof ByteBuffer) {
      return new BigDecimal(new BigInteger(Bytes.toBytes((ByteBuffer) value)), scale);
    }

    // Handle the BigDecimal value
    try {
      return new BigDecimal(new BigInteger((byte[]) value), scale);
    } catch (ClassCastException e) {
      throw new ClassCastException(String.format("Field '%s' is expected to be a decimal, but is a %s.",
                                                 logicalTypeSchema.getDisplayName(),
                                                 value.getClass().getSimpleName()));
    }
  }

  private JsonWriter getJsonWriter(Encoder encoder) {
    // Type already checked in the encode method, hence assuming the casting is fine.
    return ((JsonEncoder) encoder).getJsonWriter();
  }
}
