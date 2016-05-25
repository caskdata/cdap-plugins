/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.hydrator.plugin.batch.aggregator;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.batch.BatchAggregator;
import co.cask.cdap.etl.api.batch.BatchAggregatorContext;
import co.cask.cdap.etl.api.batch.BatchRuntimeContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batch RowDenormalizer Aggregator Plugin - It is used to de-normalize data based on the key column.
 */
@Plugin(type = BatchAggregator.PLUGIN_TYPE)
@Name("RowDenormalizer")
@Description("This plugin is used to convert raw data into denormalized data based on a key column. " +
  "User is able to specify the list of fields that should be used in the denormalized record, with " +
  "an option to use an alias for the output field name. " +
  "For example, " +
  "'ADDRESS' in the input is mapped to 'addr' in the output schema. The denormalized data is easier to query.")
public class RowDenormalizerAggregator extends BatchAggregator<String, StructuredRecord, StructuredRecord> {

  private final RowDenormalizerConfig conf;
  private Map<String, String> outputMappings;
  private Set<String> outputFields;
  private Schema outputSchema;
  private String keyField;

  public RowDenormalizerAggregator(RowDenormalizerConfig conf) {
    this.conf = conf;
  }

  @Override
  public void prepareRun(BatchAggregatorContext context) throws Exception {
    if (conf.numPartitions != null) {
      context.setNumPartitions(conf.numPartitions);
    }
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    validateInputFields(pipelineConfigurer.getStageConfigurer().getInputSchema());
    pipelineConfigurer.getStageConfigurer().setOutputSchema(initializeOutputSchema());
  }

  @Override
  public void initialize(BatchRuntimeContext context) throws Exception {
    outputFields = conf.getOutputFields();
    outputMappings = conf.getFieldAliases();
    keyField = conf.getKeyField();
    outputSchema = initializeOutputSchema();
  }

  @Override
  public void groupBy(StructuredRecord record, Emitter<String> emitter) throws Exception {
    if (record.get(keyField) == null) {
      return;
    }
    emitter.emit((String) record.get(keyField));
  }

  @Override
  public void aggregate(String groupKey, Iterator<StructuredRecord> iterator,
                        Emitter<StructuredRecord> emitter) throws Exception {
    if (!iterator.hasNext()) {
      return;
    }
    StructuredRecord.Builder builder = StructuredRecord.builder(outputSchema);
    builder.set(conf.getKeyField(), groupKey);
    while (iterator.hasNext()) {
      StructuredRecord record = iterator.next();
      String fieldName = record.get(conf.getFieldName());
      fieldName = outputMappings.containsKey(fieldName) ? outputMappings.get(fieldName) : fieldName;
      if (outputFields.contains(fieldName)) {
        builder.set(fieldName, record.get(conf.getFieldValue()));
      }
    }
    emitter.emit(builder.build());
  }

  /**
   * Builds the output schema, using output fields provided by user.
   */
  private Schema initializeOutputSchema() {
    List<Schema.Field> fields = new ArrayList<>();
    // Input fields coming from source will be of type String; that's why we are building the output schema as String.
    fields.add(Schema.Field.of(conf.getKeyField(), Schema.of(Schema.Type.STRING)));
    for (String outputField : conf.getOutputFields()) {
      fields.add(Schema.Field.of(outputField, Schema.nullableOf(Schema.of(Schema.Type.STRING))));
    }
    return Schema.recordOf("output.schema", fields);
  }

  /**
   * @param inputSchema Validates whether the keyfield, fieldname, and fieldvalue entered by the user is of type
   *                    String and present in the input schema or not.
   * @return true
   */
  private void validateInputFields(Schema inputSchema) {
    if ((inputSchema.getField(conf.getKeyField())) == null) {
      throw new IllegalArgumentException(
        String.format("Keyfield '%s' does not exist in input schema %s", conf.getKeyField(), inputSchema));
    } else if ((inputSchema.getField(conf.getFieldName())) == null) {
      throw new IllegalArgumentException(
        String.format("Fieldname '%s' does not exist in input schema %s", conf.getFieldName(), inputSchema));
    } else if ((inputSchema.getField(conf.getFieldValue())) == null) {
      throw new IllegalArgumentException(
        String.format("Fieldvalue '%s' does not exist in input schema %s", conf.getFieldValue(), inputSchema));
    } else if (!inputSchema.getField(conf.getKeyField()).getSchema().getType().equals(Schema.Type.STRING)) {
      throw new IllegalArgumentException(
        String.format("Keyfield '%s' must be of type String", conf.getKeyField()));
    } else if (!inputSchema.getField(conf.getFieldName()).getSchema().getType().equals(Schema.Type.STRING)) {
      throw new IllegalArgumentException(
        String.format("Fieldname '%s' must be of type String", conf.getFieldName()));
    } else if (!inputSchema.getField(conf.getFieldValue()).getSchema().getType().equals(Schema.Type.STRING)) {
      throw new IllegalArgumentException(
        String.format("Fieldvalue '%s' must be of type String", conf.getFieldValue()));
    }
  }
}
