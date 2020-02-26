/*
 * Copyright © 2016-2019 Cask Data, Inc.
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

package io.cdap.plugin.batch.aggregator;

import com.google.common.base.Splitter;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.StageConfigurer;
import io.cdap.cdap.etl.api.batch.BatchAggregator;
import io.cdap.cdap.etl.api.batch.BatchAggregatorContext;
import io.cdap.cdap.etl.api.batch.BatchRuntimeContext;
import io.cdap.cdap.etl.api.lineage.field.FieldOperation;
import io.cdap.cdap.etl.api.lineage.field.FieldTransformOperation;
import io.cdap.plugin.common.TransformLineageRecorderUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Distinct aggregator.
 */
@Plugin(type = BatchAggregator.PLUGIN_TYPE)
@Name("Distinct")
@Description("Deduplicates input records so that all output records are distinct. " +
  "Can optionally take a list of fields, which will project out all other fields and perform a distinct " +
  "on just those fields.")
public class DistinctAggregator extends RecordAggregator {
  private final Conf conf;
  private Iterable<String> fields;
  private Schema outputSchema;

  /**
   * Plugin Configuration
   */
  public static class Conf extends AggregatorConfig {
    @Nullable
    @Description("Optional comma-separated list of fields to perform the distinct on. If none is given, each record " +
      "will be taken as is. Otherwise, only fields in this list will be considered.")
    @Macro
    private String fields;

    Iterable<String> getFields() {
      return fields == null ? Collections.emptyList() : Splitter.on(',').trimResults().split(fields);
    }
  }

  public DistinctAggregator(Conf conf) {
    super(conf.numPartitions);
    this.conf = conf;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
    Schema inputSchema = stageConfigurer.getInputSchema();
    // if null, the input schema is unknown, or its multiple schemas.
    if (inputSchema == null) {
      stageConfigurer.setOutputSchema(null);
      return;
    }

    // otherwise, we have a constant input schema. Get the output schema and propagate the schema
    validate(inputSchema, conf.getFields(), stageConfigurer.getFailureCollector());
    stageConfigurer.setOutputSchema(getOutputSchema(inputSchema, conf.getFields()));
  }

  public void validate(Schema inputSchema, Iterable<String> fields, FailureCollector collector) {
    if (fields == null || !fields.iterator().hasNext()) {
      return;
    }

    for (String fieldName : fields) {
      Schema.Field field = inputSchema.getField(fieldName);
      if (field == null) {
        collector.addFailure(String.format("Field %s does not exist in input schema.", fieldName),
                             "Remove this field.").withConfigElement("fields", fieldName);
      }
    }
  }

  @Override
  public void prepareRun(BatchAggregatorContext context) throws Exception {
    super.prepareRun(context);

    validate(context.getInputSchema(), conf.getFields(), context.getFailureCollector());
    context.getFailureCollector().getOrThrowException();
  }

  @Override
  public void initialize(BatchRuntimeContext context) {
    outputSchema = context.getOutputSchema();
    fields = conf.getFields();
  }

  @Override
  public void groupBy(StructuredRecord record, Emitter<StructuredRecord> emitter) {
    if (fields == null) {
      emitter.emit(record);
      return;
    }

    Schema recordSchema = outputSchema == null ? getOutputSchema(record.getSchema(), fields) : outputSchema;
    StructuredRecord.Builder builder = StructuredRecord.builder(recordSchema);
    for (String fieldName : fields) {
      builder.set(fieldName, record.get(fieldName));
    }
    emitter.emit(builder.build());
  }

  @Override
  public void aggregate(StructuredRecord groupKey, Iterator<StructuredRecord> iterator,
                        Emitter<StructuredRecord> emitter) {
    emitter.emit(groupKey);
  }

  private static Schema getOutputSchema(Schema inputSchema, Iterable<String> fields) {
    if (fields == null || !fields.iterator().hasNext()) {
      return inputSchema;
    }

    List<Schema.Field> outputFields = new ArrayList<>();
    for (String fieldName : fields) {
      Schema.Field field = inputSchema.getField(fieldName);
      if (field == null) {
        throw new IllegalArgumentException(String.format("Field %s does not exist in input schema %s.",
                                                         fieldName, inputSchema));
      }
      outputFields.add(field);
    }
    return Schema.recordOf(inputSchema.getRecordName() + ".distinct", outputFields);
  }
}
