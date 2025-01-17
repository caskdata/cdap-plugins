/*
 * Copyright © 2018-2021 Cask Data, Inc.
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

package io.cdap.plugin.format.plugin;

import com.google.common.base.Strings;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.exception.ErrorCategory;
import io.cdap.cdap.api.exception.ErrorType;
import io.cdap.cdap.api.exception.ErrorUtils;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.plugin.common.IdUtils;
import io.cdap.plugin.format.FileFormat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Sink configuration.
 */
@SuppressWarnings("unused")
public abstract class AbstractFileSinkConfig extends PluginConfig implements FileSinkProperties {
  public static final String NAME_FORMAT = "format";
  public static final String NAME_SCHEMA = "schema";
  public static final String NAME_SUFFIX = "suffix";

  @Description("Name be used to uniquely identify this sink for lineage, annotating metadata, etc.")
  private String referenceName;

  @Macro
  @Nullable
  @Description("The time format for the output directory that will be appended to the path. " +
    "For example, the format 'yyyy-MM-dd-HH-mm' will result in a directory of the form '2015-01-01-20-42'. " +
    "If not specified, nothing will be appended to the path.")
  private String suffix;

  @Macro
  @Description("The format to write in. The format must be one of 'json', 'avro', 'parquet', 'csv', 'tsv', "
    + "or 'delimited'.")
  private String format;

  @Macro
  @Nullable
  @Description("The delimiter to use if the format is 'delimited'. The delimiter will be ignored if the format "
    + "is anything other than 'delimited'.")
  private String delimiter;

  @Macro
  @Nullable
  @Description("Whether a header should be written to each output file. This only applies to the delimited, csv, " +
    "and tsv formats.")
  private Boolean writeHeader;

  @Macro
  @Nullable
  @Description("The schema of the data to write.")
  private String schema;

  public void validate() {
    IdUtils.validateId(referenceName);
    if (suffix != null && !containsMacro(NAME_SUFFIX)) {
      new SimpleDateFormat(suffix);
    }
    getSchema();
  }

  public void validate(FailureCollector collector) {
    validate(collector, Collections.emptyMap());
  }

  public void validate(FailureCollector collector, Map<String, String> arguments) {
    IdUtils.validateReferenceName(referenceName, collector);
    if (suffix != null && !containsMacro(NAME_SUFFIX)) {
      try {
        new SimpleDateFormat(suffix);
      } catch (IllegalArgumentException e) {
        collector.addFailure(
                String.format("Invalid suffix, %s: %s", e.getClass().getName(), e.getMessage()),
                "Ensure provided suffix is valid.").withConfigProperty(NAME_SUFFIX)
            .withStacktrace(e.getStackTrace());
      }
    }

    // if failure collector has not collected any errors, that would mean either validation has succeeded or config
    // is using deprecated validate method without collector. In that case, call deprecated validate method.
    if (collector.getValidationFailures().isEmpty()) {
      try {
        validate();
      } catch (Exception e) {
        collector.addFailure(e.getMessage(), null).withStacktrace(e.getStackTrace());
      }
    }
  }

  @Override
  public String getReferenceName() {
    return referenceName;
  }

  @Override
  @Nullable
  public String getSuffix() {
    return suffix;
  }

  @Nullable
  public Schema getSchema() {
    if (containsMacro(NAME_SCHEMA) || Strings.isNullOrEmpty(schema)) {
      return null;
    }
    try {
      return Schema.parseJson(schema);
    } catch (IOException e) {
      String errorMessage = String.format("Invalid schema %s, %s: %s", schema,
          e.getClass().getName(), e.getMessage());
      throw ErrorUtils.getProgramFailureException(
          new ErrorCategory(ErrorCategory.ErrorCategoryEnum.PLUGIN), errorMessage, errorMessage,
          ErrorType.USER, false, e);
    }
  }

  @Override
  public String getFormatName() {
    // need to do this for backwards compatibility, where the pre-packaged format names were case insensitive.
    try {
      FileFormat fileFormat = FileFormat.from(format, x -> true);
      return fileFormat.name().toLowerCase();
    } catch (IllegalArgumentException e) {
      // ignore
    }
    return format;
  }
}
