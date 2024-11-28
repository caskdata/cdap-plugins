/*
 * Copyright © 2018-2022 Cask Data, Inc.
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

package io.cdap.plugin.format.text.input;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.plugin.PluginClass;
import io.cdap.cdap.api.plugin.PluginPropertyField;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.api.validation.ValidatingInputFormat;
import io.cdap.plugin.format.input.PathTrackingConfig;
import io.cdap.plugin.format.input.PathTrackingInputFormatProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Input reading logic for text files.
 */
@Plugin(type = ValidatingInputFormat.PLUGIN_TYPE)
@Name(TextInputFormatProvider.NAME)
@Description(TextInputFormatProvider.DESC)
public class TextInputFormatProvider extends PathTrackingInputFormatProvider<TextInputFormatProvider.TextConfig> {
  static final String NAME = "text";
  static final String DESC = "Plugin for reading files in text format.";
  public static final PluginClass PLUGIN_CLASS =
    new PluginClass(ValidatingInputFormat.PLUGIN_TYPE, NAME, DESC, TextInputFormatProvider.class.getName(),
                    "conf", TextConfig.TEXT_FIELDS);
  // this has to be here to be able to instantiate the correct plugin property field
  private final TextConfig conf;
  private static final Logger LOG = LoggerFactory.getLogger(TextInputFormatProvider.class);

  public TextInputFormatProvider(TextConfig conf) {
    super(conf);
    this.conf = conf;
  }

  @Override
  public String getInputFormatClassName() {
    Map<String, String> fsProperties = this.conf.getFileSystemProperties();
    Boolean disableCombine = Boolean.valueOf(
            fsProperties.getOrDefault(PathTrackingTextInputFormat.DISABLE_COMBINE, "false"));
    String inputFormatClassName = disableCombine ? TextInputFormat.class.getName() :
            CombineTextInputFormat.class.getName();
    LOG.debug("format-text input format class : {}", inputFormatClassName);
    return inputFormatClassName;
  }

  @Override
  protected void validate() {
    if (conf.containsMacro("schema")) {
      return;
    }

    String pathField = conf.getPathField();
    Schema schema = conf.getSchema();

    // text must contain 'body' as type 'string'.
    // it can optionally contain a 'offset' field of type 'long'
    // it can optionally contain a path field of type 'string'
    Schema.Field offsetField = schema.getField("offset");
    if (offsetField != null) {
      Schema offsetSchema = offsetField.getSchema();
      Schema.Type offsetType = offsetSchema.isNullable() ? offsetSchema.getNonNullable().getType() :
        offsetSchema.getType();
      if (offsetType != Schema.Type.LONG) {
        throw new IllegalArgumentException(String.format("The 'offset' field must be of type 'long', but found '%s'",
                                                         offsetType.name().toLowerCase()));
      }
    }

    Schema.Field bodyField = schema.getField("body");
    if (bodyField == null) {
      throw new IllegalArgumentException("The schema for the 'text' format must have a field named 'body'");
    }
    Schema bodySchema = bodyField.getSchema();
    Schema.Type bodyType = bodySchema.isNullable() ? bodySchema.getNonNullable().getType() : bodySchema.getType();
    if (bodyType != Schema.Type.STRING) {
      throw new IllegalArgumentException(String.format("The 'body' field must be of type 'string', but found '%s'",
                                                       bodyType.name().toLowerCase()));
    }

    // fields should be body (required), offset (optional), [pathfield] (optional)
    boolean expectOffset = schema.getField("offset") != null;
    boolean expectPath = pathField != null;
    int numExpectedFields = 1;
    if (expectOffset) {
      numExpectedFields++;
    }
    if (expectPath) {
      numExpectedFields++;
    }
    int maxExpectedFields = pathField == null ? 2 : 3;
    int numFields = schema.getFields().size();
    if (numFields > numExpectedFields) {
      String expectedFields;
      if (expectOffset && expectPath) {
        expectedFields = String.format("'offset', 'body', and '%s' fields", pathField);
      } else if (expectOffset) {
        expectedFields = "'offset' and 'body' fields";
      } else if (expectPath) {
        expectedFields = String.format("'body' and '%s' fields", pathField);
      } else {
        expectedFields = "'body' field";
      }

      int numExtraFields = numFields - maxExpectedFields;
      throw new IllegalArgumentException(
        String.format("The schema for the 'text' format must only contain the %s, but found %d other field%s",
                      expectedFields, numExtraFields, numExtraFields > 1 ? "s" : ""));
    }
  }

  @Override
  public void validate(FormatContext context) {
    if (conf.containsMacro(TextConfig.NAME_SCHEMA)) {
      return;
    }

    FailureCollector collector = context.getFailureCollector();
    Schema schema;
    try {
      schema = conf.getSchema();
    } catch (Exception e) {
      collector.addFailure(e.getMessage(), null).withConfigProperty(TextConfig.NAME_SCHEMA)
        .withStacktrace(e.getStackTrace());
      throw collector.getOrThrowException();
    }

    String pathField = conf.getPathField();
    // text must contain 'body' as type 'string'.
    // it can optionally contain a 'offset' field of type 'long'
    // it can optionally contain a path field of type 'string'
    Schema.Field offsetField = schema.getField(TextConfig.NAME_OFFSET);
    if (offsetField != null) {
      Schema offsetSchema = offsetField.getSchema();
      offsetSchema = offsetSchema.isNullable() ? offsetSchema.getNonNullable() : offsetSchema;
      Schema.Type offsetType = offsetSchema.getType();
      if (offsetType != Schema.Type.LONG) {
        collector.addFailure(
          String.format("The 'offset' field is of unexpected type '%s'.", offsetSchema.getDisplayName()),
          "Change type to 'long'.").withOutputSchemaField(TextConfig.NAME_OFFSET);
      }
    }

    Schema.Field bodyField = schema.getField(TextConfig.NAME_BODY);
    if (bodyField == null) {
      collector.addFailure("The schema for the 'text' format must have a field named 'body'.", null)
        .withConfigProperty(TextConfig.NAME_SCHEMA);
    } else {
      Schema bodySchema = bodyField.getSchema();
      bodySchema = bodySchema.isNullable() ? bodySchema.getNonNullable() : bodySchema;
      Schema.Type bodyType = bodySchema.getType();
      if (bodyType != Schema.Type.STRING) {
        collector.addFailure(
          String.format("The 'body' field is of unexpected type '%s'.'", bodySchema.getDisplayName()),
          "Change type to 'string'.").withOutputSchemaField(TextConfig.NAME_BODY);
      }
    }

    // fields should be body (required), offset (optional), [pathfield] (optional)
    boolean expectOffset = schema.getField(TextConfig.NAME_OFFSET) != null;
    boolean expectPath = pathField != null;
    int numExpectedFields = 1;
    if (expectOffset) {
      numExpectedFields++;
    }
    if (expectPath) {
      numExpectedFields++;
    }

    int numFields = schema.getFields().size();
    if (numFields > numExpectedFields) {
      for (Schema.Field field : schema.getFields()) {
        String expectedFields;
        if (expectOffset && expectPath) {
          expectedFields = String.format("'offset', 'body', and '%s' fields", pathField);
        } else if (expectOffset) {
          expectedFields = "'offset' and 'body' fields";
        } else if (expectPath) {
          expectedFields = String.format("'body' and '%s' fields", pathField);
        } else {
          expectedFields = "'body' field";
        }

        if (field.getName().equals(TextConfig.NAME_BODY) || (expectPath && field.getName().equals(pathField))
          || field.getName().equals(TextConfig.NAME_OFFSET)) {
          continue;
        }

        collector.addFailure(
          String.format("The schema for the 'text' format must only contain the '%s'.", expectedFields),
          String.format("Remove additional field '%s'.", field.getName())).withOutputSchemaField(field.getName());
      }
    }
  }

  public static Schema getDefaultSchema(@Nullable String pathField) {
    List<Schema.Field> fields = new ArrayList<>();
    fields.add(Schema.Field.of("offset", Schema.of(Schema.Type.LONG)));
    fields.add(Schema.Field.of("body", Schema.of(Schema.Type.STRING)));
    if (pathField != null && !pathField.isEmpty()) {
      fields.add(Schema.Field.of(pathField, Schema.of(Schema.Type.STRING)));
    }
    return Schema.recordOf("textfile", fields);
  }

  @Override
  protected void addFormatProperties(Map<String, String> properties) {
    super.addFormatProperties(properties);
    properties.put(PathTrackingTextInputFormat.SKIP_HEADER, String.valueOf(conf.getSkipHeader()));
  }

  /**
   * Text plugin config
   */
  public static class TextConfig extends PathTrackingConfig {
    public static final Map<String, PluginPropertyField> TEXT_FIELDS;
    private static final String NAME_SCHEMA = "schema";
    private static final String NAME_OFFSET = "offset";
    private static final String NAME_BODY = "body";

    private static final String SKIP_HEADER_DESC = "Whether to skip header for the files. " +
                                                     "Default value is false.";

    static {
      Map<String, PluginPropertyField> fields = new HashMap<>(FIELDS);
      fields.put("skipHeader", new PluginPropertyField("skipHeader", SKIP_HEADER_DESC,
                                                       "boolean", false, true));
      TEXT_FIELDS = Collections.unmodifiableMap(fields);
    }

    public TextConfig() {
      super();
    }

    @VisibleForTesting
    public TextConfig(String pathField) {
      super(pathField);
    }

    @Macro
    @Nullable
    @Description(SKIP_HEADER_DESC)
    protected Boolean skipHeader;

    public boolean getSkipHeader() {
      return skipHeader == null ? false : skipHeader;
    }

    /**
     * Return the configured schema, or the default schema if none was given. Should never be called if the
     * schema contains a macro
     */
    @Override
    public Schema getSchema() {
      if (containsMacro(NAME_SCHEMA)) {
        return null;
      }
      if (Strings.isNullOrEmpty(schema)) {
        return getDefaultSchema(pathField);
      }
      try {
        return Schema.parseJson(schema);
      } catch (IOException e) {
        throw new IllegalArgumentException("Invalid schema: " + e.getMessage(), e);
      }
    }
  }
}
