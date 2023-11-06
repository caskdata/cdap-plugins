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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.api.plugin.PluginPropertyField;
import io.cdap.plugin.common.KeyValueListParser;
import io.cdap.plugin.format.input.PathTrackingConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Common config for Excel related formats.
 */
public class XlsInputFormatConfig extends PathTrackingConfig {
  public static final String SHEET_NUMBER = "Sheet Number";
  private static final String NAME_OVERRIDE = "override";
  private static final String NAME_SHEET = "sheet";
  public static final String NAME_SHEET_VALUE = "sheetValue";
  private static final String NAME_SKIP_HEADER = "skipHeader";
  private static final String NAME_TERMINATE_IF_EMPTY_ROW = "terminateIfEmptyRow";

  // properties
  public static final String NAME_SAMPLE_SIZE = "sampleSize";

  public static final String DESC_SKIP_HEADER =
    "Whether to skip the first line of each sheet. The default value is false.";
  public static final String DESC_SHEET = "Select the sheet by name or number. Default is 'Sheet Number'.";
  public static final String DESC_SHEET_VALUE = "Specifies the value corresponding to 'sheet' input. " +
    "Can be either sheet name or sheet no; for example: 'Sheet1' or '0' in case user selects 'Sheet Name' or " +
    "'Sheet Number' as 'sheet' input respectively. Sheet number starts with 0. Default is 'Sheet Number' 0.";
  public static final String DESC_TERMINATE_ROW = "Specify whether to stop reading after " +
          "encountering the first empty row. Defaults to false.";
  public static final Map<String, PluginPropertyField> XLS_FIELDS;

  static {
    Map<String, PluginPropertyField> fields = new HashMap<>(FIELDS);
    fields.put(NAME_SKIP_HEADER,
            new PluginPropertyField(NAME_SKIP_HEADER, DESC_SKIP_HEADER, "boolean", false, true));
    // Add fields specific for excel format handling.
    fields.put(NAME_SHEET, new PluginPropertyField(NAME_SHEET, DESC_SHEET, "string", false, true));
    fields.put(NAME_SHEET_VALUE, new PluginPropertyField(NAME_SHEET_VALUE, DESC_SHEET_VALUE, "string", false, true));
    fields.put(NAME_TERMINATE_IF_EMPTY_ROW, new PluginPropertyField(
                       NAME_TERMINATE_IF_EMPTY_ROW, DESC_TERMINATE_ROW, "boolean", false, true));
    XLS_FIELDS = Collections.unmodifiableMap(fields);
  }

  @Macro
  @Nullable
  @Name(NAME_SHEET)
  @Description(DESC_SHEET)
  private String sheet;

  @Macro
  @Nullable
  @Name(NAME_SHEET_VALUE)
  @Description(DESC_SHEET_VALUE)
  private String sheetValue;

  @Macro
  @Nullable
  @Name(NAME_SKIP_HEADER)
  @Description(DESC_SKIP_HEADER)
  private Boolean skipHeader;

  @Macro
  @Nullable
  @Name(NAME_TERMINATE_IF_EMPTY_ROW)
  @Description(DESC_TERMINATE_ROW)
  private Boolean terminateIfEmptyRow;

  public XlsInputFormatConfig() {
    super();
  }

  @VisibleForTesting
  public XlsInputFormatConfig(@Nullable String schema, @Nullable String sheet, @Nullable String sheetValue,
                              @Nullable Boolean skipHeader, @Nullable Boolean terminateIfEmptyRow) {
    super();
    this.schema = schema;
    this.sheet = sheet;
    this.sheetValue =  sheetValue;
    this.skipHeader = skipHeader;
    this.terminateIfEmptyRow = terminateIfEmptyRow;
  }

  public int getSampleSize() {
    String sampleSize = getProperties().getProperties().getOrDefault(NAME_SAMPLE_SIZE, "1000");
    try {
      return Integer.parseInt(sampleSize);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(String.format("Invalid sample size '%s'.", sampleSize));
    }
  }

  public String getSheet() {
    return sheet == null ? SHEET_NUMBER : sheet;
  }

  @Nullable
  public String getSheetValue() {
    return sheetValue;
  }

  public boolean getSkipHeader() {
    return skipHeader != null ? skipHeader : false;
  }

  public boolean getTerminateIfEmptyRow() {
    return terminateIfEmptyRow != null ? terminateIfEmptyRow : false;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for building a {@link XlsInputFormatConfig}.
   */
  public static class Builder {
    private String schema;
    private String sheet;
    private String sheetValue;
    private Boolean skipHeader;
    private Boolean terminateIfEmptyRow;

    public Builder setSchema(String schema) {
      this.schema = schema;
      return this;
    }

    public Builder setSheet(String sheet) {
      this.sheet = sheet;
      return this;
    }

    public Builder setSheetValue(String sheetValue) {
      this.sheetValue = sheetValue;
      return this;
    }

    public Builder setSkipHeader(Boolean skipHeader) {
      this.skipHeader = skipHeader;
      return this;
    }

    public Builder setTerminateIfEmptyRow(Boolean terminateIfEmptyRow) {
      this.terminateIfEmptyRow = terminateIfEmptyRow;
      return this;
    }

    public XlsInputFormatConfig build() {
     return new XlsInputFormatConfig(schema, sheet, sheetValue, skipHeader, terminateIfEmptyRow);
    }
  }

}
