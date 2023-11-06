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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Unit tests for {@link XlsInputFormatSchemaDetector}
 */
public class XlsInputFormatSchemaDetectorTest {
  Method isCellEmptyMethod;
  Method getSchemaTypeMethod;
  Method reduceSchemaTypeMethod;
  Workbook workbook;
  Sheet sheet;
  XlsInputFormatSchemaDetector xlsInputFormatSchemaDetector;

  @Before
  public void setUp() throws Exception {
    xlsInputFormatSchemaDetector = new XlsInputFormatSchemaDetector();
    isCellEmptyMethod = xlsInputFormatSchemaDetector.getClass().getDeclaredMethod("isCellEmpty", Cell.class);
    isCellEmptyMethod.setAccessible(true);
    getSchemaTypeMethod = xlsInputFormatSchemaDetector.getClass().getDeclaredMethod("getSchemaType", Cell.class);
    getSchemaTypeMethod.setAccessible(true);
    reduceSchemaTypeMethod = xlsInputFormatSchemaDetector.getClass().getDeclaredMethod("reduceSchemaType",
            Schema.Type.class, Cell.class);
    reduceSchemaTypeMethod.setAccessible(true);
    // Mock XLS File
    boolean newXssfFile = true;
    workbook = WorkbookFactory.create(newXssfFile);
    sheet = workbook.createSheet("sheet");
  }

  @Test
  public void testIsCellEmptyMethod() throws IOException, InvocationTargetException, IllegalAccessException {
    Row row = sheet.createRow(0);
    int testColumn = 1;
    Cell blankCell = row.createCell(++testColumn);
    blankCell.setBlank();
    Assert.assertEquals(true, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, blankCell));

    Cell stringCell = row.createCell(++testColumn);
    stringCell.setCellValue("string");
    Assert.assertEquals(false, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, stringCell));

    Cell numericCell = row.createCell(++testColumn);
    numericCell.setCellValue(1.0);
    Assert.assertEquals(false, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, numericCell));

    Cell booleanCell = row.createCell(++testColumn);
    booleanCell.setCellValue(true);
    Assert.assertEquals(false, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, booleanCell));

    Cell formulaCell = row.createCell(++testColumn);
    formulaCell.setCellFormula("SUM(A1:B1)");
    Assert.assertEquals(false, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, formulaCell));

    Cell errorCell = row.createCell(++testColumn);
    errorCell.setCellErrorValue(FormulaError.DIV0.getCode());
    Assert.assertEquals(false, isCellEmptyMethod.invoke(xlsInputFormatSchemaDetector, errorCell));
    workbook.close();
  }

  @Test
  public void testGetSchemaTypeMethod() throws IOException, InvocationTargetException, IllegalAccessException {
    Row row = sheet.createRow(0);
    int testColumn = 1;
    Cell stringCell = row.createCell(++testColumn);
    stringCell.setCellValue("string");
    Assert.assertEquals(Schema.Type.STRING, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, stringCell));

    Cell numericCell = row.createCell(++testColumn);
    numericCell.setCellValue(1.0);
    Assert.assertEquals(Schema.Type.DOUBLE, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, numericCell));

    Cell booleanCell = row.createCell(++testColumn);
    booleanCell.setCellValue(true);
    Assert.assertEquals(Schema.Type.BOOLEAN, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, booleanCell));

    Cell errorCell = row.createCell(++testColumn);
    errorCell.setCellErrorValue(FormulaError.DIV0.getCode());
    Assert.assertEquals(Schema.Type.STRING, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, errorCell));

    workbook.close();
  }

  @Test
  public void testGetSchemaTypeMethodWithFormula() throws IOException, InvocationTargetException,
          IllegalAccessException {
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
    evaluator.evaluateAll();

    Assert.assertEquals(numericValue1 + numericValue2, formulaCell.getNumericCellValue(), 0.0);
    Assert.assertEquals(Schema.Type.DOUBLE, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, formulaCell));
    Assert.assertEquals(stringValue1 + stringValue2, formulaCell2.getStringCellValue());
    Assert.assertEquals(Schema.Type.STRING, getSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector, formulaCell2));

    workbook.close();
  }

  @Test
  public void testReduceSchemaType() throws InvocationTargetException, IllegalAccessException {
    Row row = sheet.createRow(0);
    int testColumn = 1;

    // CDAP $TYPE + XLS_CELL $TPYE = CDAP $TYPE
    // STRING     + ANY            = STRING
    Cell stringCell = row.createCell(++testColumn);
    stringCell.setCellValue("string");
    Assert.assertEquals(Schema.Type.STRING, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.STRING, stringCell));
    Cell numericCell = row.createCell(++testColumn);
    numericCell.setCellValue(1.0);
    Assert.assertEquals(Schema.Type.STRING, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.STRING, numericCell));
    Cell booleanCell = row.createCell(++testColumn);
    booleanCell.setCellValue(true);
    Assert.assertEquals(Schema.Type.STRING, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.STRING, booleanCell));
    Cell errorCell = row.createCell(++testColumn);
    errorCell.setCellErrorValue(FormulaError.DIV0.getCode());
    Assert.assertEquals(Schema.Type.STRING, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.STRING, errorCell));
    Cell formulaCell = row.createCell(++testColumn);
    formulaCell.setCellFormula("SUM(A1:B1)");
    Assert.assertEquals(Schema.Type.STRING, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.STRING, formulaCell));

    // BOOLEAN + BOOLEAN = BOOLEAN
    // BOOLEAN + NUMERIC = DOUBLE
    Cell booleanCell2 = row.createCell(++testColumn);
    booleanCell2.setCellValue(true);
    Assert.assertEquals(Schema.Type.BOOLEAN, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.BOOLEAN, booleanCell2));
    Cell numericCell2 = row.createCell(++testColumn);
    numericCell2.setCellValue(1.0);
    Assert.assertEquals(Schema.Type.DOUBLE, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.BOOLEAN, numericCell2));

    // DOUBLE + NUMERIC = DOUBLE
    Cell numericCell3 = row.createCell(++testColumn);
    numericCell3.setCellValue(1.0);
    Assert.assertEquals(Schema.Type.DOUBLE, reduceSchemaTypeMethod.invoke(xlsInputFormatSchemaDetector,
            Schema.Type.DOUBLE, numericCell3));
  }
}
