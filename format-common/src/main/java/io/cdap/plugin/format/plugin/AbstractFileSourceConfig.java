/*
 * Copyright © 2018-2020 Cask Data, Inc.
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
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.plugin.common.IdUtils;
import io.cdap.plugin.format.FileFormat;
import io.cdap.plugin.format.charset.fixedlength.FixedLengthCharset;

import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A default implementation of {@link FileSourceProperties}. Extend this class only if the plugin does not
 * need to change any of the property descriptions, names, macros, etc.
 */
@SuppressWarnings({"unused", "ConstantConditions"})
public abstract class AbstractFileSourceConfig extends PluginConfig implements FileSourceProperties {
  public static final String NAME_FORMAT = "format";
  public static final String NAME_SCHEMA = "schema";
  public static final String NAME_DELIMITER = "delimiter";
  public static final String DEFAULT_FILE_ENCODING = "UTF-8";

  @Description("Name be used to uniquely identify this source for lineage, annotating metadata, etc.")
  @Nullable
  private String referenceName;

  @Macro
  @Nullable
  @Description("Regular expression that file paths must match in order to be included in the input. "
    + "The full file path is compared, not just the file name."
    + "If no value is given, no file filtering will be done. "
    + "See https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html for more information about "
    + "the regular expression syntax.")
  private String fileRegex;

  @Macro
  @Description("Format of the data to read. Supported formats are 'avro', 'blob', 'csv', 'delimited', 'json', "
    + "'parquet', 'text', or 'tsv'. ")
  private String format;

  @Nullable
  @Description("Maximum size of each partition used to read data. "
    + "Smaller partitions will increase the level of parallelism, but will require more resources and overhead.")
  @Macro
  private Long maxSplitSize;

  @Macro
  @Nullable
  @Description("Whether to allow an input that does not exist. When false, the source will fail the run if the input "
    + "does not exist. When true, the run will not fail and the source will not generate any output. "
    + "The default value is false.")
  private Boolean ignoreNonExistingFolders;

  @Macro
  @Nullable
  @Description("Whether to recursively read directories within the input directory. The default is false.")
  private Boolean recursive;

  @Name(PATH_FIELD)
  @Macro
  @Nullable
  @Description("Output field to place the path of the file that the record was read from. "
    + "If not specified, the file path will not be included in output records. "
    + "If specified, the field must exist in the output schema as a string.")
  private String pathField;

  @Macro
  @Nullable
  @Description("Whether to only use the filename instead of the URI of the file path when a path field is given. "
    + "The default value is false.")
  private Boolean filenameOnly;

  @Macro
  @Nullable
  @Description("Output schema for the source. Formats like 'avro' and 'parquet' require a schema in order to "
    + "read the data.")
  private String schema;

  @Name(NAME_DELIMITER)
  @Macro
  @Nullable
  @Description("The delimiter to use if the format is 'delimited'. The delimiter will be ignored if the format "
    + "is anything other than 'delimited'.")
  private String delimiter;

  @Macro
  @Nullable
  @Description("Whether to use first row as header. Supported formats are 'text', 'csv', 'tsv', " +
    "'delimited'. Default value is false.")
  private Boolean skipHeader;

  @Macro
  @Nullable
  @Description("Whether to treat content between quotes as a value. This value will only be used if the format " +
    "is 'csv', 'tsv' or 'delimited'. The default value is false.")
  protected Boolean enableQuotedValues;

  @Macro
  @Nullable
  @Description("File encoding for the source files. The default encoding is 'UTF-8'")
  private String fileEncoding;

  // this is a hidden property that only exists for wrangler's parse-as-csv that uses the header as the schema
  // when this is true and the format is text, the header will be the first record returned by every record reader
  @Nullable
  private Boolean copyHeader;

  protected AbstractFileSourceConfig() {
    // empty constructor is used to set default values.
    format = FileFormat.TEXT.name().toLowerCase();
    maxSplitSize = 128L * 1024 * 1024;
    ignoreNonExistingFolders = false;
    recursive = false;
    filenameOnly = false;
    copyHeader = false;
  }

  public void validate() {
    IdUtils.validateId(referenceName);
    getSchema();
  }

  public void validate(FailureCollector collector) {
    if (!Strings.isNullOrEmpty(referenceName)) {
      IdUtils.validateReferenceName(referenceName, collector);
    }
    try {
      getSchema();
    } catch (IllegalArgumentException e) {
      collector.addFailure(e.getMessage(), null).withConfigProperty(NAME_SCHEMA).withStacktrace(e.getStackTrace());
    }

    if (getFileEncoding() != null && !getFileEncoding().equals(getDefaultFileEncoding())) {
      if (!FixedLengthCharset.isValidEncoding(getFileEncoding())) {
        collector.addFailure("Specified file encoding is not valid.",
                             "Use one of the supported file encodings.");
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

  @Nullable
  @Override
  public Pattern getFilePattern() {
    try {
      return Strings.isNullOrEmpty(fileRegex) ? null : Pattern.compile(fileRegex);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid file regular expression." + e.getMessage(), e);
    }
  }

  @Override
  public long getMaxSplitSize() {
    return maxSplitSize;
  }

  @Override
  public boolean shouldAllowEmptyInput() {
    return ignoreNonExistingFolders;
  }

  @Override
  public boolean shouldReadRecursively() {
    return recursive;
  }

  @Nullable
  @Override
  public String getPathField() {
    return pathField;
  }

  @Override
  public boolean useFilenameAsPath() {
    return filenameOnly;
  }

  @Override
  public boolean skipHeader() {
    return skipHeader == null ? false : skipHeader;
  }

  @Nullable
  public String getFileEncoding() {
    String encoding = fileEncoding == null || fileEncoding.isEmpty() ?
      DEFAULT_FILE_ENCODING : fileEncoding;

    return FixedLengthCharset.cleanFileEncodingName(encoding);
  }

  public String getDefaultFileEncoding() {
    return DEFAULT_FILE_ENCODING;
  }

  @Nullable
  public Schema getSchema() {
    try {
      return containsMacro(NAME_SCHEMA) || Strings.isNullOrEmpty(schema) ? null : Schema.parseJson(schema);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid schema: " + e.getMessage(), e);
    }
  }

  public boolean shouldCopyHeader() {
    return copyHeader;
  }
}
