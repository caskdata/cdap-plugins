/*
 * Copyright © 2017-2019 Cask Data, Inc.
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

package io.cdap.plugin.batch.source;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.plugin.format.FileFormat;
import io.cdap.plugin.format.plugin.AbstractFileSourceConfig;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * File source config
 */
public class FileSourceConfig extends AbstractFileSourceConfig {
  public static final String NAME_FILE_SYSTEM_PROPERTIES = "fileSystemProperties";
  public static final String NAME_PATH = "path";
  public static final String NAME_FILE_ENCODING = "fileEncoding";
  public static final String NAME_SHEET = "sheet";
  public static final String NAME_SHEET_VALUE = "sheetValue";
  public static final String NAME_TERMINATE_IF_EMPTY_ROW = "terminateIfEmptyRow";

  private static final Gson GSON = new Gson();
  private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() { }.getType();

  @Macro
  @Description("Path to file(s) to be read. If a directory is specified, " +
    "terminate the path name with a \'/\'. For distributed file system such as HDFS, file system name should come"  +
    "from 'fs.DefaultFS' property in the 'core-site.xml'. For example, 'hdfs://mycluster.net:8020/input', where " +
    "value of the property 'fs.DefaultFS' in the 'core-site.xml' is 'hdfs://mycluster.net:8020'.")
  private String path;

  @Macro
  @Nullable
  @Description("Any additional properties to use when reading from the filesystem. "
    + "This is an advanced feature that requires knowledge of the properties supported by the underlying filesystem.")
  private String fileSystemProperties;

  @Macro
  @Nullable
  @Description("A list of columns with the corresponding data types for whom the automatic data type detection gets " +
    "skipped.")
  private String override;

  @Macro
  @Nullable
  @Description("The maximum number of rows that will get investigated for automatic data type detection.")
  private Long sampleSize;

  @Name(NAME_SHEET)
  @Macro
  @Nullable
  @Description("Select the sheet by name or number. Default is 'Sheet Number'.")
  private String sheet;

  @Name(NAME_SHEET_VALUE)
  @Macro
  @Nullable
  @Description("The name/number of the sheet to read from. If not specified, the first sheet will be read." +
          "Sheet Number are 0 based, ie first sheet is 0.")
  private String sheetValue;

  @Name(NAME_TERMINATE_IF_EMPTY_ROW)
  @Macro
  @Nullable
  @Description("Specify whether to stop reading after encountering the first empty row. Defaults to false.")
  private String terminateIfEmptyRow;
  
  FileSourceConfig() {
    super();
    fileSystemProperties = "{}";
  }

  @Override
  public void validate() {
    super.validate();
    getFileSystemProperties();
  }

  public void validate(FailureCollector collector) {
    super.validate(collector);
    try {
      getFileSystemProperties();
    } catch (IllegalArgumentException e) {
      collector.addFailure("File system properties must be a valid json.", null)
        .withConfigProperty(NAME_FILE_SYSTEM_PROPERTIES).withStacktrace(e.getStackTrace());
    }
    validateSchemaDelimitedFormats(collector);
  }

  public void validateSchemaDelimitedFormats(FailureCollector collector) {
    if (getSchema() == null) {
      return;
    }

    if (containsMacro(NAME_FORMAT)) {
      return;
    }

    String formatName = getFormatName();
    if (formatName.equalsIgnoreCase(FileFormat.CSV.name())
      || formatName.equalsIgnoreCase(FileFormat.TSV.name())
      || formatName.equalsIgnoreCase(FileFormat.DELIMITED.name())) {
      for (Schema.Field field : getSchema().getFields()) {
        Schema.LogicalType type = field.getSchema().getLogicalType();
        if (type == null) {
          continue;
        }
        if (type.equals(Schema.LogicalType.TIME_MICROS) || type.equals(Schema.LogicalType.TIME_MILLIS)
        || type.equals(Schema.LogicalType.DATE)) {
          collector.addFailure(
            String.format("Type '%s' in schema is not supported for '%s' format.",
                          type.toString().toLowerCase(), formatName),
           "Supported data types are: 'string', 'timestamp' and 'date'"
          ).withConfigProperty(NAME_FORMAT).withOutputSchemaField(field.getName());
        }
      }
    }
  }

  Map<String, String> getFileSystemProperties() {
    if (fileSystemProperties == null) {
      return new HashMap<>();
    }
    try {
      return GSON.fromJson(fileSystemProperties, MAP_STRING_STRING_TYPE);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse filesystem properties: " + e.getMessage(), e);
    }
  }

  public String getPath() {
    return path;
  }
}
