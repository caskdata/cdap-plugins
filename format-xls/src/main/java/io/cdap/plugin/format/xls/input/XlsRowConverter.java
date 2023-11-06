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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Converts a row of XLS cells to a StructuredRecord.
 */
public class XlsRowConverter {
  private final FormulaEvaluator evaluator;
  private static final DataFormatter dataFormatter = new DataFormatter();

  XlsRowConverter(FormulaEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * Converts a row of XLS cells to a StructuredRecord.
   * Returns null if the row is null or empty.
   */
  @Nullable
  public StructuredRecord.Builder convert(Row row, Schema outputSchema) {
    if (row == null) {
      return null;
    }
    boolean isRowEmpty = true;
    StructuredRecord.Builder builder = StructuredRecord.builder(outputSchema);
    List<Schema.Field> fields = outputSchema.getFields();
    for (int cellIndex = 0; cellIndex < row.getLastCellNum() && cellIndex < fields.size(); cellIndex++) {
      Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      if (cell == null) {
        // Blank cells are skipped, builder will set null for the field, no processing needed.
        continue;
      }
      Schema.Field field = fields.get(cellIndex);
      Schema.Type type = field.getSchema().isNullable() ?
              field.getSchema().getNonNullable().getType() : field.getSchema().getType();
      Object cellValue;
      switch (type) {
        case STRING:
          cellValue = getCellAsString(cell);
          break;
        case DOUBLE:
          cellValue = getCellAsDouble(cell);
          break;
        case BOOLEAN:
          cellValue = getCellAsBoolean(cell);
          break;
        default:
          // As we only support string, double and boolean, this should never happen.
          throw new IllegalStateException(
                  String.format("Field '%s' is of unsupported type '%s'. Supported types are: %s",
                          field.getName(), type, "string, double, boolean"));
      }
      if (cellValue == null) {
        continue;
      }
      builder.set(field.getName(), cellValue);
      isRowEmpty = false;
    }
    if (isRowEmpty) {
      return null;
    }
    return builder;
  }

  private CellType getCellType(Cell cell) {
    CellType cellType = cell.getCellType();
    if (cellType == CellType.FORMULA) {
      try {
        cellType = cell.getCachedFormulaResultType();
      } catch (Exception e) {
        cellType = evaluator.evaluateFormulaCell(cell);
      }
    }
    return cellType;
  }

  private String getCellAsString(Cell cell) {
    CellType cellType = getCellType(cell);

    switch (cellType) {
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return dataFormatter.formatCellValue(cell);
        }
        return Double.toString(cell.getNumericCellValue());
      case STRING:
        return cell.getRichStringCellValue().getString();
      case BOOLEAN:
        return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
      case BLANK:
      case ERROR:
        return null;
      default:
        throw new IllegalStateException(
                String.format("Failed to format (%s) due to unsupported cell type (%s)", cell, cellType));
    }
  }

  private boolean getCellAsBoolean(Cell cell) {
    CellType cellType = getCellType(cell);

    switch (cellType) {
      case NUMERIC:
        // Non-zero values are true
        return cell.getNumericCellValue() != 0;
      case STRING:
        return cell.getRichStringCellValue().getString().equalsIgnoreCase("true");
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case BLANK:
      case ERROR:
        return false;
      default:
        throw new IllegalStateException(
                String.format("Failed to format (%s) due to unsupported cell type (%s)", cell, cellType));
    }
  }

  private Double getCellAsDouble(Cell cell) {
    CellType cellType = getCellType(cell);

    switch (cellType) {
      case NUMERIC:
        return cell.getNumericCellValue();
      case STRING:
        return null;
      case BOOLEAN:
        return cell.getBooleanCellValue() ? 1.0 : 0.0;
      case BLANK:
      case ERROR:
        return 0.0;
      default:
        throw new IllegalStateException(
                String.format("Failed to format (%s) due to unsupported cell type (%s)", cell, cellType));
    }
  }

}
