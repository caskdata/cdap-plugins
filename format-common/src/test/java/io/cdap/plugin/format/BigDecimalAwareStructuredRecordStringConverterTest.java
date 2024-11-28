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

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;

public class BigDecimalAwareStructuredRecordStringConverterTest {
  private static final Schema SCHEMA = Schema.recordOf(
    "rec",
    Schema.Field.of("i", Schema.of(Schema.Type.INT)),
    Schema.Field.of("f", Schema.of(Schema.Type.FLOAT)),
    Schema.Field.of("bd", Schema.nullableOf(Schema.decimalOf(32, 7))),
    Schema.Field.of("d", Schema.of(Schema.Type.DOUBLE)),
    Schema.Field.of("l", Schema.of(Schema.Type.LONG)));

  private static final BigDecimal BD1 =
    new BigDecimal("12398321.8127312", MathContext.DECIMAL128).setScale(7);

  private static final BigDecimal BD2 =
    new BigDecimal("1000000000000000.0", MathContext.DECIMAL128).setScale(7);

  private static final StructuredRecord RECORD1 = StructuredRecord.builder(SCHEMA)
    .set("i", 1)
    .set("f", 256.1f)
    .setDecimal("bd", BD1)
    .set("d", 12345.45678d)
    .set("l", 123456789L)
    .build();

  private static final StructuredRecord RECORD2 = StructuredRecord.builder(SCHEMA)
    .set("i", 1)
    .set("f", 256.1f)
    .setDecimal("bd", BD2)
    .set("d", 12345.45678d)
    .set("l", 123456789L)
    .build();

  private static final StructuredRecord RECORD3 = StructuredRecord.builder(SCHEMA)
    .set("i", 1)
    .set("f", 256.1f)
    .setDecimal("bd", null)
    .set("d", 12345.45678d)
    .set("l", 123456789L)
    .build();

  @Test
  public void testToJsonString1() throws IOException {
    String output = BigDecimalAwareStructuredRecordStringConverter.toJsonString(RECORD1);
    Assert.assertTrue(output.startsWith("{\"i\":1,"));
    Assert.assertTrue(output.contains(",\"f\":256.1"));
    Assert.assertTrue(output.contains(",\"bd\":12398321.8127312,"));
    Assert.assertTrue(output.contains(",\"d\":12345.45678,"));
    Assert.assertTrue(output.endsWith(",\"l\":123456789}"));
  }

  @Test
  public void testToJsonString2() throws IOException {
    String output = BigDecimalAwareStructuredRecordStringConverter.toJsonString(RECORD2);
    Assert.assertTrue(output.startsWith("{\"i\":1,"));
    Assert.assertTrue(output.contains(",\"f\":256.1"));
    Assert.assertTrue(output.contains(",\"bd\":1000000000000000.0000000,"));
    Assert.assertTrue(output.contains(",\"d\":12345.45678,"));
    Assert.assertTrue(output.endsWith(",\"l\":123456789}"));
  }

  @Test
  public void testToJsonString3() throws IOException {
    String output = BigDecimalAwareStructuredRecordStringConverter.toJsonString(RECORD3);
    Assert.assertTrue(output.startsWith("{\"i\":1,"));
    Assert.assertTrue(output.contains(",\"f\":256.1"));
    Assert.assertTrue(output.contains(",\"bd\":null,"));
    Assert.assertTrue(output.contains(",\"d\":12345.45678,"));
    Assert.assertTrue(output.endsWith(",\"l\":123456789}"));
  }

  @Test
  public void testToDelimitedStringUsingComma1() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD1, ",");
    Assert.assertTrue(output.startsWith("1,"));
    Assert.assertTrue(output.contains(",256.1,"));
    Assert.assertTrue(output.contains(",12398321.8127312,"));
    Assert.assertTrue(output.contains(",12345.45678,"));
    Assert.assertTrue(output.endsWith(",123456789"));
  }

  @Test
  public void testToDelimitedStringUsingComma2() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD2, ",");
    Assert.assertTrue(output.startsWith("1,"));
    Assert.assertTrue(output.contains(",256.1,"));
    Assert.assertTrue(output.contains(",1000000000000000.0000000,"));
    Assert.assertTrue(output.contains(",12345.45678,"));
    Assert.assertTrue(output.endsWith(",123456789"));
  }

  @Test
  public void testToDelimitedStringUsingComma3() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD3, ",");
    Assert.assertTrue(output.startsWith("1,"));
    Assert.assertTrue(output.contains(",256.1,"));
    Assert.assertTrue(output.contains(",,"));
    Assert.assertTrue(output.contains(",12345.45678,"));
    Assert.assertTrue(output.endsWith(",123456789"));
  }

  @Test
  public void testToDelimitedStringUsingPipe1() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD1, "|");
    Assert.assertTrue(output.startsWith("1|"));
    Assert.assertTrue(output.contains("|256.1|"));
    Assert.assertTrue(output.contains("|12398321.8127312|"));
    Assert.assertTrue(output.contains("|12345.45678|"));
    Assert.assertTrue(output.endsWith("|123456789"));
  }

  @Test
  public void testToDelimitedStringUsingPipe2() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD2, "|");
    Assert.assertTrue(output.startsWith("1|"));
    Assert.assertTrue(output.contains("|256.1|"));
    Assert.assertTrue(output.contains("|1000000000000000.0000000|"));
    Assert.assertTrue(output.contains("|12345.45678|"));
    Assert.assertTrue(output.endsWith("|123456789"));
  }

  @Test
  public void testToDelimitedStringUsingPipe3() {
    String output = BigDecimalAwareStructuredRecordStringConverter.toDelimitedString(RECORD3, "|");
    Assert.assertTrue(output.startsWith("1|"));
    Assert.assertTrue(output.contains("|256.1|"));
    Assert.assertTrue(output.contains("||"));
    Assert.assertTrue(output.contains("|12345.45678|"));
    Assert.assertTrue(output.endsWith("|123456789"));
  }
}
