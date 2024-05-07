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

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Unit tests for {@link XlsRowConverter}
 */
public class XlsRowConverterTest {
  Workbook workbook;
  Sheet sheet;

  @Before
  public void setUp() throws IOException {
    // Mock XLS File
    boolean newXssfFile = true;
    workbook = WorkbookFactory.create(newXssfFile);
    sheet = workbook.createSheet("sheet");
  }

  @Test
  public void testFormatCellValue() {
    Row row = sheet.createRow(0);
    row.createCell(0).setCellValue("test");
    int testColumn = 0;

    Cell blankCell = row.createCell(++testColumn);
    blankCell.setBlank();

    Cell booleanCell = row.createCell(++testColumn);
    booleanCell.setCellValue(true);

    Cell numericCell = row.createCell(++testColumn);
    numericCell.setCellValue(1.0);

    Cell stringCell = row.createCell(++testColumn);
    stringCell.setCellValue("test");

    Cell errorCell = row.createCell(++testColumn);
    errorCell.setCellErrorValue(FormulaError.DIV0.getCode());

    Schema outputSchema = Schema.recordOf(
            "record",
            Schema.Field.of("string", Schema.of(Schema.Type.STRING)),
            Schema.Field.of("blank", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
            Schema.Field.of("boolean", Schema.of(Schema.Type.BOOLEAN)),
            Schema.Field.of("numeric", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("string2", Schema.of(Schema.Type.STRING)),
            Schema.Field.of("error", Schema.nullableOf(Schema.of(Schema.Type.STRING)))
    );
    XlsRowConverter rowConverter = new XlsRowConverter(workbook.getCreationHelper().createFormulaEvaluator());
    StructuredRecord record = rowConverter.convert(row, outputSchema).build();
    Assert.assertEquals("test", record.get("string"));
    Assert.assertNull(record.get("blank"));
    Assert.assertEquals(true, record.get("boolean"));
    Assert.assertEquals(1.0, record.get("numeric"), 0.0001);
    Assert.assertEquals("test", record.get("string2"));
    Assert.assertNull(record.get("error"));
  }

  @Test
  public void testFormatCellValueWithCachedFormulaResult() {
    Row row = sheet.createRow(0);
    double numericValue1 = 1.0;
    double numericValue2 = 2.0;
    Cell a1Numeric = row.createCell(0);
    a1Numeric.setCellValue(numericValue1);
    Cell b1Numeric = row.createCell(1);
    b1Numeric.setCellValue(numericValue2);

    Row row2 = sheet.createRow(1);
    String stringValue1 = "hello";
    String stringValue2 = "world";
    Cell a2String = row2.createCell(0);
    a2String.setCellValue(stringValue1);
    Cell b2String = row2.createCell(1);
    b2String.setCellValue(stringValue2);

    Row row3 = sheet.createRow(2);
    Cell formulaCell = row3.createCell(0);
    formulaCell.setCellFormula("SUM(A1:B1)");
    Cell formulaCell2 = row3.createCell(1);
    formulaCell2.setCellFormula("CONCAT(A2:B2)");

    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
    // Cache the formula results
    evaluator.evaluateAll();

    XlsRowConverter xlsRowConverter = new XlsRowConverter(evaluator);
    Schema outputSchema = Schema.recordOf(
            "record",
            Schema.Field.of("numeric", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("string", Schema.of(Schema.Type.STRING))
    );
    StructuredRecord record = xlsRowConverter.convert(row3, outputSchema).build();
    Assert.assertEquals(3.0, record.get("numeric"), 0.0001);
    Assert.assertEquals("helloworld", record.get("string"));
  }
}
