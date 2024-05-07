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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Detects the schema of an Excel file.
 */
public class XlsInputFormatSchemaDetector {

  private final Map<Integer, Schema.Type> columnSchemaReducerMap = new HashMap<>();
  private final Map<Integer, Boolean> columnNullableMap = new HashMap<>();

  /**
   * Reduces the schema of the Excel file.
   *
   * @param columnIndex the column index of the cell
   * @param cell the cell to reduce the schema from
   * @param isFirstRow whether the cell is in the first row
   */
  public void reduceSchema(int columnIndex, Cell cell, boolean isFirstRow) {
    boolean isCellEmpty = isCellEmpty(cell);

    if (!columnNullableMap.containsKey(columnIndex)) {
      // When we see the index for the first time and this is not the first row,
      // we can assume that the column is nullable as the previous rows did not have a value for this column.
      columnNullableMap.put(columnIndex, !isFirstRow);
    }
    // Pin the nullability of the column to true if the cell is empty
    columnNullableMap.put(columnIndex, isCellEmpty || columnNullableMap.get(columnIndex));
    if (isCellEmpty) {
      return;
    }
    // Check if key exists in map
    if (columnSchemaReducerMap.containsKey(columnIndex)) {
      // If key exists, reduce the schema type
      columnSchemaReducerMap.put(columnIndex, reduceSchemaType(columnSchemaReducerMap.get(columnIndex), cell));
    } else {
      // If key does not exist, add it to the map
      columnSchemaReducerMap.put(columnIndex, getSchemaType(cell));
    }
  }

  private void normalizeColumn(int numColumns) {
    for (int i = 0; i < numColumns; i++) {
      // set all nullability to true if not present
      columnNullableMap.putIfAbsent(i, true);
      // set all schema types to string if not present
      columnSchemaReducerMap.putIfAbsent(i, Schema.Type.STRING);
    }
  }

  /**
   * Returns the schema of the Excel file.
   *
   * @param columnNames the column names of the Excel file
   * @return the schema of the Excel file
   */
  public List<Schema.Field> getFields(List<String> columnNames) {
    normalizeColumn(columnNames.size());
    List<Schema.Field> fields = new ArrayList<>();
    for (int i = 0; i < columnNames.size(); i++) {
      String columnName = columnNames.get(i);
      boolean isNullable = columnNullableMap.get(i);
      Schema.Type schemaType = columnSchemaReducerMap.get(i);
      Schema schema = isNullable ? Schema.nullableOf(Schema.of(schemaType)) : Schema.of(schemaType);
      fields.add(Schema.Field.of(columnName, schema));
    }
    return fields;
  }

  private static boolean isCellEmpty(Cell cell) {
    if (cell != null && cell.getCellType() == CellType.FORMULA) {
      return cell.getCachedFormulaResultType() == CellType.BLANK;
    }
    return cell == null || cell.getCellType() == CellType.BLANK;
  }

  private static Schema.Type getSchemaType(Cell cell) {
    CellType cellType = cell.getCellType() == CellType.FORMULA ?
                    cell.getCachedFormulaResultType() : cell.getCellType();
    // Force Dates As String
    if (cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return Schema.Type.STRING;
    }
    // Mapping for XLS Cell Types to CDAP Schema Types
    switch (cellType) {
      case BOOLEAN:
        return Schema.Type.BOOLEAN;
      case NUMERIC:
        return Schema.Type.DOUBLE;
      default:
        return Schema.Type.STRING;
    }
  }
  private static Schema.Type reduceSchemaType(Schema.Type detectedSchemaType, Cell cell) {
    if (detectedSchemaType == Schema.Type.STRING) {
      return Schema.Type.STRING;
    }
    CellType cellType = cell.getCellType() == CellType.FORMULA ?
            cell.getCachedFormulaResultType() : cell.getCellType();
    switch (cellType) {
      case BOOLEAN:
        switch (detectedSchemaType) {
          case BOOLEAN:
            return Schema.Type.BOOLEAN;
          case DOUBLE:
            return Schema.Type.DOUBLE;
        }
        return Schema.Type.STRING;
      case NUMERIC:
        return Schema.Type.DOUBLE;
    }
    return Schema.Type.STRING;
  }
}
