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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilities around XLS input format.
 */
public class XlsInputFormatUtils {
  private static final Pattern NOT_VALID_PATTERN = Pattern.compile("[^A-Za-z0-9_]+");

  /**
   * Cleans a list of column names to make sure they comply with avro field naming standard.
   * It also makes sure each name is unique in the list.
   * Field names can start with [A-Za-z_] and subsequently contain only [A-Za-z0-9_].
   * <p>
   * Steps:
   * 1) Trim surrounding spaces
   * 2) If its empty replace it with BLANK
   * 3) If it starts with a number, prepend "col_"
   * 4) Replace invalid characters with "_" (multiple invalid characters gets replaced with one symbol)
   * 5) Check if the name has been found before (without considering case)
   * if so add _# where # is the number of times seen before + 1
   */
  public static List<String> getSafeColumnNames(List<String> columnNames) {
    return cleanSchemaColumnNames(columnNames);
  }

  private static List<String> cleanSchemaColumnNames(List<String> columnNames) {
    final String replacementChar = "_";
    final List<String> cleanColumnNames = new ArrayList<>();
    final Map<String, Integer> seenColumnNames = new HashMap<>();
    for (String columnName : columnNames) {
      StringBuilder cleanColumnNameBuilder = new StringBuilder();

      // Remove any spaces at the end of the strings
      columnName = columnName.trim();

      // If it's an empty string replace it with BLANK
      if (columnName.isEmpty()) {
        cleanColumnNameBuilder.append("BLANK");
      } else if ((columnName.charAt(0) >= '0') && (columnName.charAt(0) <= '9')) {
        // Prepend a col_ if the first character is a number
        cleanColumnNameBuilder.append("col_");
      }

      // Replace all invalid characters with the replacement char
      cleanColumnNameBuilder.append(NOT_VALID_PATTERN.matcher(columnName).replaceAll(replacementChar));

      // Check if the field exist if so append and index at the end
      // We use lowercase to match columns "A" and "a" to avoid issues with wrangler.
      String cleanColumnName = cleanColumnNameBuilder.toString();
      String lowerCaseCleanColumnName = cleanColumnName.toLowerCase();
      while (seenColumnNames.containsKey(lowerCaseCleanColumnName)) {
        cleanColumnNameBuilder.append(replacementChar).append(seenColumnNames.get(lowerCaseCleanColumnName));
        seenColumnNames.put(lowerCaseCleanColumnName, seenColumnNames.get(lowerCaseCleanColumnName) + 1);
        cleanColumnName = cleanColumnNameBuilder.toString();
        lowerCaseCleanColumnName = cleanColumnName.toLowerCase();
      }
      seenColumnNames.put(lowerCaseCleanColumnName, 2);

      cleanColumnNames.add(cleanColumnName);
    }
    return cleanColumnNames;
  }
}
