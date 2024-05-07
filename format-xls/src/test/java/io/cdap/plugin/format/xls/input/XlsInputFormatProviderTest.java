/*
 * Copyright © 2024 Cask Data, Inc.
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

package io.cdap.plugin.format.xls.input;

import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.mock.validation.MockFailureCollector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link XlsInputFormatProvider}
 */
public class XlsInputFormatProviderTest {
  XlsInputFormatProvider xlsInputFormatProvider;
  MockFailureCollector failureCollector;
  FormatContext formatContext;
  String validSchemaString;
  XlsInputFormatConfig.Builder xlsInputFormatConfigBuilder;

  @Before
  public void setup() {
    failureCollector = new MockFailureCollector();
    formatContext = new FormatContext(failureCollector, null);
    xlsInputFormatConfigBuilder = XlsInputFormatConfig.builder();
    validSchemaString = Schema.recordOf("test",
                    Schema.Field.of("test", Schema.of(Schema.Type.STRING))).toString();
  }

  @Test
  public void testValidateInvalidSheetNumber() {
    xlsInputFormatProvider = new XlsInputFormatProvider(xlsInputFormatConfigBuilder
            .setSheet(XlsInputFormatConfig.SHEET_NUMBER)
            .setSheetValue("A")
            .setSchema(validSchemaString).build());
    xlsInputFormatProvider.validate(formatContext);
    Assert.assertEquals(1, failureCollector.getValidationFailures().size());
    Assert.assertEquals("Sheet number must be a number.",
            failureCollector.getValidationFailures().get(0).getMessage());
  }

  @Test
  public void testValidateValidSheetNumber() {
    xlsInputFormatProvider = new XlsInputFormatProvider(xlsInputFormatConfigBuilder
            .setSheet(XlsInputFormatConfig.SHEET_NUMBER)
            .setSheetValue("0")
            .setSchema(validSchemaString).build());
    xlsInputFormatProvider.validate(formatContext);
    Assert.assertEquals(0, failureCollector.getValidationFailures().size());
  }

  @Test
  public void testValidateWithNoSchema() {
    xlsInputFormatProvider = new XlsInputFormatProvider(xlsInputFormatConfigBuilder
            .setSheet(XlsInputFormatConfig.SHEET_NUMBER)
            .setSheetValue("0")
            .build());
    xlsInputFormatProvider.validate(formatContext);
    Assert.assertEquals(1, failureCollector.getValidationFailures().size());
    Assert.assertEquals("XLS format cannot be used without specifying a schema.",
            failureCollector.getValidationFailures().get(0).getMessage());
  }
}

