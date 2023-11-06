/*
 * Copyright © 2018-2019 Cask Data, Inc.
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

package io.cdap.plugin.format;

import io.cdap.cdap.api.data.schema.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * FileFormat supported by the file based sources/sinks. Some formats can be used for both reading and writing.
 * TODO: remove once formats have completely been converted to plugins
 */
public enum FileFormat {
  AVRO(true, true),
  BLOB(true, false),
  CSV(true, true),
  DELIMITED(true, true),
  JSON(true, true),
  ORC(false, true),
  PARQUET(true, true),
  TEXT(true, false),
  TSV(true, true),
  XLS(true, false);
  private final boolean canRead;
  private final boolean canWrite;

  FileFormat(boolean canRead, boolean canWrite) {
    this.canRead = canRead;
    this.canWrite = canWrite;
  }

  public boolean canRead() {
    return canRead;
  }

  public boolean canWrite() {
    return canWrite;
  }

  /**
   * Return the schema for this format, if the format requires a specific schema. Returns null if the format does
   * not require a specific schema. Should only be called for formats that can read.
   *
   * @param pathField the field of the file path, if it exists.
   * @return the schema required by the format, if it exists
   */
  @Nullable
  public Schema getSchema(@Nullable String pathField) {
    // TODO: move into the plugin formats once it is possible to instantiate them in the get schema methods.
    List<Schema.Field> fields = new ArrayList<>(3);
    switch (this) {
      case TEXT:
        fields.add(Schema.Field.of("offset", Schema.of(Schema.Type.LONG)));
        fields.add(Schema.Field.of("body", Schema.of(Schema.Type.STRING)));
        if (pathField != null) {
          fields.add(Schema.Field.of(pathField, Schema.of(Schema.Type.STRING)));
        }
        return Schema.recordOf("text", fields);
      case BLOB:
        fields.add(Schema.Field.of("body", Schema.of(Schema.Type.BYTES)));
        if (pathField != null) {
          fields.add(Schema.Field.of(pathField, Schema.of(Schema.Type.STRING)));
        }
        return Schema.recordOf("text", fields);
      default:
        return null;
    }
  }

  /**
   * Get a FileFormat from the specified string. This is similar to the valueOf method except that the error
   * message will contain the full set of valid values. It also supports filtering which enum values are valid.
   * This can be used to only get FileFormats that can be used for reading or only get formats that can be used
   * for writing.
   *
   * @param format the format to get
   * @param isValidFormat a filter used to only allow certain enum values
   * @return the FileFormat corresponding to the specified string
   * @throws IllegalArgumentException if the specified format does not have an equivalent value that also satisfies the
   *   specified predicate
   */
  public static FileFormat from(String format, Predicate<FileFormat> isValidFormat) {
    FileFormat fileFormat;
    try {
      fileFormat = valueOf(format.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(getExceptionMessage(format, isValidFormat));
    }

    if (!isValidFormat.test(fileFormat)) {
      throw new IllegalArgumentException(getExceptionMessage(format, isValidFormat));
    }

    return fileFormat;
  }

  /**
   * Return an error message that enumerates all valid values that are acceptable.
   */
  private static String getExceptionMessage(String format, Predicate<FileFormat> isValid) {
    String values = Arrays.stream(FileFormat.values())
      .filter(isValid)
      .map(f -> f.name().toLowerCase())
      .collect(Collectors.joining(", "));
    throw new IllegalArgumentException(String.format("Invalid format '%s'. The value must be one of %s",
                                                     format, values));
  }
}
