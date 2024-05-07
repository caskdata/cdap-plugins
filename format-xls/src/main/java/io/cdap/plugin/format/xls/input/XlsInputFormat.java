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
import io.cdap.plugin.format.input.PathTrackingInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import javax.annotation.Nullable;


/**
 * {@link XlsInputFormat} is {@link TextInputFormat} implementation for reading Excel files.
 * <p>
 * The {@link XlsInputFormat.XlsRecordReader} reads a given sheet, and within a sheet reads
 * all columns and all rows.
 */
public class XlsInputFormat extends PathTrackingInputFormat {

  public static final String SHEET_NUM = "Sheet Number";
  public static final String SHEET_VALUE = "sheetValue";
  public static final String NAME_SKIP_HEADER = "skipHeader";
  public static final String TERMINATE_IF_EMPTY_ROW = "terminateIfEmptyRow";

  @Override
  protected RecordReader<NullWritable, StructuredRecord.Builder> createRecordReader(
          FileSplit split, TaskAttemptContext context, @Nullable String pathField,
          @Nullable Schema schema) throws IOException {
    Configuration jobConf = context.getConfiguration();
    boolean skipFirstRow = jobConf.getBoolean(NAME_SKIP_HEADER, false);
    boolean terminateIfEmptyRow = jobConf.getBoolean(TERMINATE_IF_EMPTY_ROW, false);
    Schema outputSchema = schema != null ? Schema.parseJson(context.getConfiguration().get("schema")) : null;
    String sheet = jobConf.get(SHEET_NUM);
    String sheetValue = jobConf.get(SHEET_VALUE, "0");
    return new XlsRecordReader(sheet, sheetValue, outputSchema, terminateIfEmptyRow, skipFirstRow);
  }

  /**
   * Reads Excel sheet, where each row is a {@link StructuredRecord} and each cell is a field in the record.
   */
  public static class XlsRecordReader extends RecordReader<NullWritable, StructuredRecord.Builder> {
    // Converter for converting xls row to structured record
    XlsRowConverter rowConverter;
    FormulaEvaluator formulaEvaluator;
    // Builder for building structured record
    private StructuredRecord.Builder valueBuilder;
    private Sheet workSheet;
    // InputStream handler for Excel files.
    private FSDataInputStream fileIn;
    // Specifies the row index.
    private int rowIndex;
    // Specifies last row num.
    private int lastRowNum;
    private boolean isRowNull;
    private final String sheet;
    private final String sheetValue;
    private final Schema outputSchema;
    private final boolean terminateIfEmptyRow;
    private final boolean skipFirstRow;

    /**
     * Constructor for XlsRecordReader.
     */
    public XlsRecordReader(String sheet, String sheetValue, Schema outputSchema, boolean terminateIfEmptyRow,
                           boolean skipFirstRow) {
      this.sheet = sheet;
      this.sheetValue = sheetValue;
      this.outputSchema = outputSchema;
      this.terminateIfEmptyRow = terminateIfEmptyRow;
      this.skipFirstRow = skipFirstRow;
    }

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context) throws IOException {

      if (!(split instanceof FileSplit)) {
        // should never happen
        throw new IllegalStateException("Input split is not a FileSplit.");
      }
      FileSplit fileSplit = (FileSplit) split;
      Configuration jobConf = context.getConfiguration();
      // Path of input file.
      Path file = fileSplit.getPath();
      FileSystem fs = file.getFileSystem(jobConf);
      fileIn = fs.open(file);

      try (Workbook workbook = WorkbookFactory.create(fileIn)) {
        formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
        formulaEvaluator.setIgnoreMissingWorkbooks(true);
        // Check if user wants to access with name or number
        if (sheet.equals(XlsInputFormatConfig.SHEET_NUMBER)) {
          workSheet = workbook.getSheetAt(Integer.parseInt(sheetValue));
        } else {
          workSheet = workbook.getSheet(sheetValue);
        }
        rowConverter = new XlsRowConverter(formulaEvaluator);
      } catch (Exception e) {
        throw new IOException("Exception while reading excel sheet. " + e.getMessage(), e);
      }

      lastRowNum = workSheet.getLastRowNum();
      isRowNull = false;
      rowIndex = skipFirstRow ? 1 : 0;
      valueBuilder = StructuredRecord.builder(outputSchema);
    }

    @Override
    public boolean nextKeyValue() {
      // If any is true, then we stop processing.
      if (rowIndex > lastRowNum || lastRowNum == -1 || (isRowNull && terminateIfEmptyRow)) {
        return false;
      }
      // Get the next row.
      Row row = workSheet.getRow(rowIndex);
      valueBuilder = rowConverter.convert(row, outputSchema);
      if (row == null || valueBuilder == null) {
        isRowNull = true;
        // set valueBuilder to a new builder with all fields set to null
        valueBuilder = StructuredRecord.builder(outputSchema);
      }
      // if all fields are null, then the row is null
      rowIndex++;

      // Stop processing if the row is null and terminateIfEmptyRow is true.
      return !isRowNull || !terminateIfEmptyRow;
    }

    @Override
    public float getProgress() {
      return (float) rowIndex / lastRowNum;
    }

    @Override
    public void close() throws IOException {
      if (fileIn != null) {
        fileIn.close();
      }
    }

    @Override
    public NullWritable getCurrentKey() {
      return NullWritable.get();
    }

    @Override
    public StructuredRecord.Builder getCurrentValue() {
      return valueBuilder;
    }
  }
}
