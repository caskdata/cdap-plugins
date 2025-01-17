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

package io.cdap.plugin.format.plugin;

import com.google.common.base.Strings;
import io.cdap.cdap.api.data.batch.Input;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.api.exception.ErrorCategory;
import io.cdap.cdap.api.exception.ErrorType;
import io.cdap.cdap.api.exception.ErrorUtils;
import io.cdap.cdap.api.plugin.InvalidPluginConfigException;
import io.cdap.cdap.api.plugin.InvalidPluginProperty;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import io.cdap.cdap.etl.api.exception.ErrorDetailsProviderSpec;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.api.validation.ValidatingInputFormat;
import io.cdap.plugin.common.LineageRecorder;
import io.cdap.plugin.common.SourceInputFormatProvider;
import io.cdap.plugin.common.batch.JobUtils;
import io.cdap.plugin.format.FileFormat;
import io.cdap.plugin.format.RegexPathFilter;
import io.cdap.plugin.format.SchemaDetector;
import io.cdap.plugin.format.input.EmptyInputFormat;
import io.cdap.plugin.format.input.PathTrackingInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Common logic for a source that reads from a Hadoop FileSystem. Supports functionality that is common across any
 * FileSystem, whether it be HDFS, GCS, S3, Azure, etc.
 *
 * {@link FileSourceProperties} contains the set of properties that this source understands.
 *
 * Plugins should extend this class and simply provide any additional Configuration properties that are required
 * by their specific FileSystem, such as credential information.
 * Their PluginConfig should implement FileSourceProperties and be passed into the constructor of this class.
 *
 * @param <T> type of config
 */
public abstract class AbstractFileSource<T extends PluginConfig & FileSourceProperties>
  extends BatchSource<NullWritable, StructuredRecord, StructuredRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractFileSource.class);
  private static final String NAME_FORMAT = "format";
  private final T config;

  protected AbstractFileSource(T config) {
    this.config = config;
  }

  /**
   * Override this to provide the class name of the Empty InputFormat
   * to use when the input path does not exist.
   * If not overridden, ClassNotFound exception will be thrown when the input path does not exist.
   */
  protected String getEmptyInputFormatClassName() {
    return EmptyInputFormat.class.getName();
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    FailureCollector collector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    config.validate(collector);
    // throw exception if there were any errors while validating the config. This could happen if format or schema is
    // invalid
    collector.getOrThrowException();

    if (config.containsMacro(NAME_FORMAT)) {
      // Deploy all format plugins. This ensures that the required plugin is available when
      // the format macro is evaluated in prepareRun.
      for (FileFormat f : FileFormat.values()) {
        try {
          pipelineConfigurer.usePlugin(ValidatingInputFormat.PLUGIN_TYPE, f.name().toLowerCase(),
                                       f.name().toLowerCase(), config.getRawProperties());
        } catch (InvalidPluginConfigException e) {
          LOG.warn("Failed to register format '{}', which means it cannot be used when the pipeline is run. " +
                     "Missing properties: {}, invalid properties: {}", f.name(), e.getMissingProperties(),
                   e.getInvalidProperties().stream().map(InvalidPluginProperty::getName).collect(Collectors.toList()));
        }
      }
      return;
    }

    String fileFormat = config.getFormatName();

    // here set the schema from the config since the input format can only provide schema if all the required fields
    // are not macro
    Schema schema = config.getSchema();

    ValidatingInputFormat validatingInputFormat = getValidatingInputFormat(pipelineConfigurer);
    FormatContext context = new FormatContext(collector, null);

    if (validatingInputFormat != null && schema == null && !config.containsMacro("schema")) {
      // some formats like text, blob, etc have static schemas
      schema = validatingInputFormat.getSchema(context);
      if (schema == null && shouldGetSchema()) {
        try {
          SchemaDetector schemaDetector = new SchemaDetector(validatingInputFormat);
          schema = schemaDetector.detectSchema(config.getPath(), config.getFilePattern(),
                                               context, getFileSystemProperties(null));
        } catch (IOException e) {
          collector.addFailure(
              String.format("Error when trying to detect schema, %s: %s ", e.getClass().getName(),
                  e.getMessage()), null).withStacktrace(e.getStackTrace());
        }
      }
    }
    context = new FormatContext(collector, schema);
    validateInputFormatProvider(context, fileFormat, validatingInputFormat);
    validatePathField(collector, schema);
    pipelineConfigurer.getStageConfigurer().setOutputSchema(schema);
  }

  protected ValidatingInputFormat getValidatingInputFormat(PipelineConfigurer pipelineConfigurer) {
    return pipelineConfigurer.usePlugin(ValidatingInputFormat.PLUGIN_TYPE, config.getFormatName(),
                                        config.getFormatName(), config.getRawProperties());
  }

  @Override
  public void prepareRun(BatchSourceContext context) {
    FailureCollector collector = context.getFailureCollector();
    config.validate(collector);
    String fileFormat = config.getFormatName();
    ValidatingInputFormat validatingInputFormat = getInputFormatForRun(context, collector);

    FormatContext formatContext = new FormatContext(collector, null);
    Schema schema = context.getOutputSchema() == null ?
      validatingInputFormat.getSchema(formatContext) : context.getOutputSchema();
    Pattern pattern = config.getFilePattern();
    if (schema == null) {
      SchemaDetector schemaDetector = new SchemaDetector(validatingInputFormat);
      try {
        schema = schemaDetector.detectSchema(config.getPath(context), pattern,
                                             formatContext, getFileSystemProperties(null));
      } catch (IOException e) {
        collector.addFailure(
            String.format("Failed to auto-detect schema, %s: %s", e.getClass().getName(),
                e.getMessage()), null).withStacktrace(e.getStackTrace());
        collector.getOrThrowException();
      }
    }
    formatContext = new FormatContext(collector, schema);
    validateInputFormatProvider(formatContext, fileFormat, validatingInputFormat);
    validatePathField(collector, schema);
    collector.getOrThrowException();

    Job job = null;
    try {
      job = JobUtils.createInstance();
    } catch (IOException e) {
      collector.addFailure(
          String.format("Failed to create job instance, %s: %s", e.getClass().getName(),
              e.getMessage()), null).withStacktrace(e.getStackTrace());
      collector.getOrThrowException();
    }
    Configuration conf = job.getConfiguration();

    if (pattern != null) {
      RegexPathFilter.configure(conf, pattern);
      FileInputFormat.setInputPathFilter(job, RegexPathFilter.class);
    }
    FileInputFormat.setInputDirRecursive(job, config.shouldReadRecursively());

    LineageRecorder lineageRecorder = getLineageRecorder(context);
    lineageRecorder.createExternalDataset(schema);

    if (schema != null && schema.getFields() != null) {
      recordLineage(lineageRecorder,
                    schema.getFields().stream().map(Schema.Field::getName).collect(Collectors.toList()));
    }

    // set entries here, before FileSystem is used
    for (Map.Entry<String, String> entry : getFileSystemProperties(context).entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }

    Path path = new Path(config.getPath(context));
    FileSystem pathFileSystem = null;
    try {
      pathFileSystem = FileSystem.get(path.toUri(), conf);
    } catch (IOException e) {
      collector.addFailure(String.format("Error fetching FileSystem at path %s, %s: %s", path,
          e.getClass().getName(), e.getMessage()), null).withStacktrace(e.getStackTrace());
      collector.getOrThrowException();
    }

    FileStatus[] fileStatus = null;
    try {
      fileStatus = pathFileSystem.globStatus(path);
    } catch (IOException e) {
      collector.addFailure(String.format("Failed to get file status for path %s, %s: %s", path,
          e.getClass().getName(), e.getMessage()), null).withStacktrace(e.getStackTrace());
      collector.getOrThrowException();
    }

    String inputFormatClass;
    if (fileStatus == null) {
      if (config.shouldAllowEmptyInput()) {
        inputFormatClass = getEmptyInputFormatClassName();
      } else {
        String errorReason = String.format("Input path %s does not exist", path);
        throw ErrorUtils.getProgramFailureException(new ErrorCategory(ErrorCategory.ErrorCategoryEnum.PLUGIN),
          errorReason, errorReason, ErrorType.USER, false, null);
      }
    } else {
      try {
        FileInputFormat.addInputPath(job, path);
      } catch (IOException e) {
        collector.addFailure(
            String.format("Failed to add input path %s, %s: %s", path, e.getClass().getName(),
                e.getMessage()), null).withStacktrace(e.getStackTrace());
        collector.getOrThrowException();
      }
      FileInputFormat.setMaxInputSplitSize(job, config.getMaxSplitSize());
      inputFormatClass = validatingInputFormat.getInputFormatClassName();
      Configuration hConf = job.getConfiguration();
      Map<String, String> inputFormatConfiguration = validatingInputFormat.getInputFormatConfiguration();
      for (Map.Entry<String, String> propertyEntry : inputFormatConfiguration.entrySet()) {
        hConf.set(propertyEntry.getKey(), propertyEntry.getValue());
      }
      if (schema != null) {
        // schema will not be in the inputformat configuration if it was auto-detected, so need to add it here
        hConf.set(PathTrackingInputFormat.SCHEMA, schema.toString());
      }
    }

    // set entries here again, in case anything set by PathTrackingInputFormat should be overridden
    for (Map.Entry<String, String> entry : getFileSystemProperties(context).entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }
    if (!Strings.isNullOrEmpty(getErrorDetailsProviderClassName())) {
      context.setErrorDetailsProvider(
          new ErrorDetailsProviderSpec(getErrorDetailsProviderClassName()));
    }
    context.setInput(Input.of(config.getReferenceName(), new SourceInputFormatProvider(inputFormatClass, conf)));
  }

  protected String getErrorDetailsProviderClassName() {
    return null;
  }

  protected ValidatingInputFormat getInputFormatForRun(BatchSourceContext context,
      FailureCollector collector) {
    String fileFormat = config.getFormatName();
    try {
      return context.newPluginInstance(fileFormat);
    } catch (InvalidPluginConfigException e) {
      Set<String> properties = new HashSet<>(e.getMissingProperties());
      for (InvalidPluginProperty invalidProperty : e.getInvalidProperties()) {
        properties.add(invalidProperty.getName());
      }
      String errorMessage = String.format("Format '%s' cannot be used because properties %s "
              + "were not provided or were invalid when the pipeline was deployed. "
              + "Set the format to a different value, "
              + "or re-create the pipeline with all required properties, %s: %s", fileFormat,
          properties, e.getClass().getName(), e.getMessage());
      throw ErrorUtils.getProgramFailureException(
          new ErrorCategory(ErrorCategory.ErrorCategoryEnum.PLUGIN), errorMessage, errorMessage,
          ErrorType.USER, false, e);
    } catch (InstantiationException e) {
      collector.addFailure(String.format("Could not load the input format %s. %s: %s", fileFormat,
              e.getClass().getName(), e.getMessage()), null)
          .withPluginNotFound(fileFormat, fileFormat, ValidatingInputFormat.PLUGIN_TYPE)
          .withStacktrace(e.getStackTrace());
      throw collector.getOrThrowException();
    }
  }

  @Override
  public void transform(KeyValue<NullWritable, StructuredRecord> input,
                        Emitter<StructuredRecord> emitter) throws Exception {
    emitter.emit(input.getValue());
  }

  /**
   * Override this to provide any additional Configuration properties that are required by the FileSystem.
   * For example, if the FileSystem requires setting properties for credentials, those should be returned by
   * this method.
   */
  protected Map<String, String> getFileSystemProperties(@Nullable BatchSourceContext context) {
    return Collections.emptyMap();
  }

  /**
   * Override this to specify a custom field level operation name and description.
   */
  protected void recordLineage(LineageRecorder lineageRecorder, List<String> outputFields) {
    lineageRecorder.recordRead("Read", String.format("Read from %s files.", config.getFormatName()), outputFields);
  }

  private void validateInputFormatProvider(FormatContext context, String fileFormat,
                                           @Nullable ValidatingInputFormat validatingInputFormat) {
    FailureCollector collector = context.getFailureCollector();
    if (validatingInputFormat == null) {
      collector.addFailure(
        String.format("Could not find the '%s' input format.", fileFormat), null)
        .withPluginNotFound(fileFormat, fileFormat, ValidatingInputFormat.PLUGIN_TYPE);
    } else {
      validatingInputFormat.validate(context);
    }
  }

  /**
   * Override this to specify a custom asset instead of referenceName for the lineage recorder
   */
  protected LineageRecorder getLineageRecorder(BatchSourceContext context) {
    return new LineageRecorder(context, config.getReferenceName());
  }

  private void validatePathField(FailureCollector collector, Schema schema) {
    String pathField = config.getPathField();
    if (pathField != null && schema != null) {
      Schema.Field schemaPathField = schema.getField(pathField);
      if (schemaPathField == null) {
        collector.addFailure(String.format("Path field '%s' must exist in input schema.", pathField), null)
          .withConfigProperty(AbstractFileSourceConfig.PATH_FIELD);
        throw collector.getOrThrowException();
      }
      Schema pathFieldSchema = schemaPathField.getSchema();
      Schema nonNullableSchema = pathFieldSchema.isNullable() ? pathFieldSchema.getNonNullable() : pathFieldSchema;

      if (nonNullableSchema.getType() != Schema.Type.STRING) {
        collector.addFailure(String.format("Path field '%s' is of unsupported type '%s'.", pathField,
                                           nonNullableSchema.getDisplayName()),
                             "It must be of type 'string'.")
          .withConfigProperty(AbstractFileSourceConfig.PATH_FIELD)
          .withOutputSchemaField(schemaPathField.getName());
      }
    }
  }

  /**
   * Determines if the schema should be auto detected. This method must return false if any of the plugin properties
   * needed to determine the schema is a macro or not present. Otherwise this method should return true.
   * See PLUGIN-508 and CDAP-17473
   */
  protected boolean shouldGetSchema() {
    return true;
  }

}
