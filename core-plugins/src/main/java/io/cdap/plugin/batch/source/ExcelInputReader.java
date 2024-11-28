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

package io.cdap.plugin.batch.source;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.batch.Input;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.DatasetManagementException;
import io.cdap.cdap.api.dataset.DatasetProperties;
import io.cdap.cdap.api.dataset.lib.CloseableIterator;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.api.dataset.lib.KeyValueTable;
import io.cdap.cdap.api.dataset.table.Table;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchRuntimeContext;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import io.cdap.plugin.common.LineageRecorder;
import io.cdap.plugin.common.Properties;
import io.cdap.plugin.common.ReferencePluginConfig;
import io.cdap.plugin.common.SourceInputFormatProvider;
import io.cdap.plugin.common.TransformLineageRecorderUtils;
import io.cdap.plugin.common.batch.JobUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 *Reads excel file(s) rows and convert them to structure records.
 */
@Plugin(type = BatchSource.PLUGIN_TYPE)
@Name("Excel")
@Description("Batch Source to read one or more excel files and convert rows to structure records using " +
  "input columns, column-mapping and field-type mapping.")
public class ExcelInputReader extends BatchSource<LongWritable, Object, StructuredRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(ExcelInputReader.class);

  private static final String KEY = "key";
  private static final String FILE = "file";
  private static final String SHEET = "sheet";
  private static final String SHEET_VALUE = "sheetValue";
  private static final String TABLE_EXPIRY_PERIOD = "tableExpiryPeriod";
  private static final String ROWS_LIMIT = "rowsLimit";
  private static final String COLUMN_LIST = "columnList";
  private static final String OUTPUT_SCHEMA = "outputSchema";
  private static final String COLUMN_MAPPING = "columnMapping";
  private static final String IF_ERROR_RECORD = "ifErrorRecord";
  private static final String ERROR_DATASET_NAME = "errorDatasetName";
  private static final String RECORD = "record";
  private static final String EXIT_ON_ERROR = "Exit on error";
  private static final String WRITE_ERROR_DATASET = "Write to error dataset";
  private static final String NULL = "";
  private static final String END = "END";
  private static final String SHEET_NO = "Sheet Number";

  // Non-printable ASCII character(EOT) to seperate column-values
  private static final String CELL_SEPERATION = String.valueOf((char) 4);
  private static final String COLUMN_SEPERATION = "\r";

  private static final Gson GSON = new Gson();
  private static final Type ARRAYLIST_PREPROCESSED_FILES = new TypeToken<ArrayList<String>>() { }.getType();

  private final ExcelInputReaderConfig excelInputreaderConfig;
  private Schema outputSchema;
  private Map<String, String> columnMapping = new HashMap<>();
  private Map<String, String> outputSchemaMapping = new HashMap<>();
  private List<String> inputColumns;
  private Map<String, String> outputFieldsMapping = new HashMap<>();
  private BatchRuntimeContext batchRuntimeContext;
  private int prevRowNum;

  public ExcelInputReader(ExcelInputReaderConfig excelReaderConfig) {
    this.excelInputreaderConfig = excelReaderConfig;
  }

  private Schema errorRecordSchema =
    Schema.recordOf("schema",
                    Schema.Field.of(KEY, Schema.of(Schema.Type.STRING)),
                    Schema.Field.of(FILE, Schema.of(Schema.Type.STRING)),
                    Schema.Field.of(SHEET, Schema.of(Schema.Type.STRING)),
                    Schema.Field.of(RECORD, Schema.of(Schema.Type.STRING)));

  @Override
  public void initialize(BatchRuntimeContext context) throws Exception {
    batchRuntimeContext = context;
    FailureCollector collector = context.getFailureCollector();
    init(collector);
    collector.getOrThrowException();
  }

  /**
   * Initialize and set maps from input config object
   */
  private void init(FailureCollector collector) {
    if (!Strings.isNullOrEmpty(excelInputreaderConfig.columnList)) {
      String[] columnsList = excelInputreaderConfig.columnList.split(",");
      inputColumns = Arrays.asList(columnsList);
    }

    if (!Strings.isNullOrEmpty(excelInputreaderConfig.columnMapping)) {
      String[] mappings = excelInputreaderConfig.columnMapping.split(",");
      for (String map : mappings) {
        String[] columns = map.split(":");
        if (columns.length < 2 || Strings.isNullOrEmpty(columns[1])) {
          collector.addFailure(
            String.format("Column: '%s' in 'Column-Label Mapping' must have an alias specified.", columns[0]), null)
            .withConfigElement(COLUMN_MAPPING, map);
        } else if (CollectionUtils.isNotEmpty(inputColumns) && !inputColumns.contains(columns[0])) {
          collector.addFailure(
            String.format("Column: '%s' in 'Column-Label Mapping' " +
                            "must be included in 'Column To Be Extracted'", columns[0]), null)
            .withConfigElement(COLUMN_MAPPING, map);
        } else {
          columnMapping.put(columns[0], columns[1]);
        }
      }
    }

    if (!Strings.isNullOrEmpty(excelInputreaderConfig.outputSchema)) {
      String[] schemaList = excelInputreaderConfig.outputSchema.split(",");
      for (String schema : schemaList) {
        String[] columns = schema.split(":");
        if (columns.length < 2 || Strings.isNullOrEmpty(columns[1])) {
          collector.addFailure(
            String.format("Column: '%s' in 'Field Name Schema Type Mapping' must have an type.", columns[0]), null)
            .withConfigElement(OUTPUT_SCHEMA, schema);
        } else if (CollectionUtils.isNotEmpty(inputColumns) && !inputColumns.contains(columns[0])) {
          collector.addFailure(
            String.format("Column: '%s' in 'Field Name Schema Type Mapping' " +
                            "must be included in 'Column To Be Extracted'", columns[0]), null)
            .withConfigElement(OUTPUT_SCHEMA, schema);
        } else {
          outputSchemaMapping.put(columns[0], columns[1]);
        }
      }
    }
  }

  @Override
  public void transform(KeyValue<LongWritable, Object> input, Emitter<StructuredRecord> emitter) throws Exception {

    getOutputSchema();
    StructuredRecord.Builder builder = StructuredRecord.builder(outputSchema);
    String inputValue = input.getValue().toString();

    String[] excelRecord = inputValue.split(CELL_SEPERATION);

    String fileName = excelRecord[1];
    String sheetName = excelRecord[2];
    String ifEndRow = excelRecord[3];

    int currentRowNum = Integer.parseInt(excelRecord[0]);
    if (currentRowNum - prevRowNum > 1 && excelInputreaderConfig.terminateIfEmptyRow.equalsIgnoreCase("true")) {
      throw new ExecutionException("Encountered empty row while reading Excel file :" + fileName +
                                     " . Terminating processing", new Throwable());
    }
    prevRowNum = currentRowNum;

    Map<String, String> excelColumnValueMap = new HashMap<>();

    for (String column : excelRecord) {
      String[] columnValue = column.split(COLUMN_SEPERATION);
      if (columnValue.length > 1) {
        String name = columnValue[0];
        String value = columnValue[1];
        if (columnMapping.containsKey(name)) {
          excelColumnValueMap.put(columnMapping.get(name), value);
        } else {
          excelColumnValueMap.put(name, value);
        }
      }
    }

    try {
      for (Schema.Field field : outputSchema.getFields()) {
        String fieldName = field.getName();
        if (excelColumnValueMap.containsKey(fieldName)) {
          builder.convertAndSet(fieldName, excelColumnValueMap.get(fieldName));
        } else {
          builder.set(fieldName, NULL);
        }
      }

      builder.set(FILE, new Path(fileName).getName());
      builder.set(SHEET, sheetName);

      emitter.emit(builder.build());

      if (ifEndRow.equalsIgnoreCase(END) && !Strings.isNullOrEmpty(excelInputreaderConfig.memoryTableName)) {
        KeyValueTable processedFileMemoryTable = batchRuntimeContext.getDataset(excelInputreaderConfig.memoryTableName);
        processedFileMemoryTable.write(Bytes.toBytes(fileName), Bytes.toBytes(new Date().getTime()));
      }
    } catch (Exception e) {
      switch (excelInputreaderConfig.ifErrorRecord) {
        case EXIT_ON_ERROR:
          throw new IllegalStateException("Terminating processing on error : " + e.getMessage());
        case WRITE_ERROR_DATASET:
          StructuredRecord.Builder errorRecordBuilder = StructuredRecord.builder(errorRecordSchema);
          errorRecordBuilder.set(KEY, fileName + "_" + sheetName + "_" + excelRecord[0]);
          errorRecordBuilder.set(FILE, fileName);
          errorRecordBuilder.set(SHEET, sheetName);
          errorRecordBuilder.set(RECORD, inputValue);
          Table errorTable = batchRuntimeContext.getDataset(excelInputreaderConfig.errorDatasetName);
          errorTable.write(errorRecordBuilder.build());
          break;
        default:
          //ignore on error
          LOG.error("Error while reading excel input: ", e);
          break;
      }
    }
  }

  /**
   * Returns list of all the processed file names which are kept in memory table.
   * @param batchSourceContext
   * @return processedFiles
   */
  private List<String> getAllProcessedFiles(BatchSourceContext batchSourceContext) {
    List<String> processedFiles = new ArrayList<>();
    if (!excelInputreaderConfig.reprocess) {
      KeyValueTable table = batchSourceContext.getDataset(excelInputreaderConfig.memoryTableName);
      processedFiles = new ArrayList<>();
      Calendar cal = Calendar.getInstance();
      int expiryDays = 30;
      if (!Strings.isNullOrEmpty(excelInputreaderConfig.tableExpiryPeriod)) {
        expiryDays = Integer.valueOf(excelInputreaderConfig.tableExpiryPeriod);
      }
      cal.add(Calendar.DATE, -expiryDays);
      Date expiryDate = cal.getTime();
      try (CloseableIterator<KeyValue<byte[], byte[]>> filesIterable = table.scan(null, null)) {
        while (filesIterable.hasNext()) {
          KeyValue<byte[], byte[]> file = filesIterable.next();
          Long time = Bytes.toLong(file.getValue());
          Date processedDate = new Date(time);
          if (processedDate.before(expiryDate)) {
            table.delete(file.getKey());
          } else {
            processedFiles.add(new String(file.getKey(), Charsets.UTF_8));
          }
        }
      }
    }
    return processedFiles;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    super.configurePipeline(pipelineConfigurer);

    FailureCollector collector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    excelInputreaderConfig.validate(collector);

    createDatasets(pipelineConfigurer);
    collector.getOrThrowException();
    init(collector);
    collector.getOrThrowException();
    getOutputSchema();
    pipelineConfigurer.getStageConfigurer().setOutputSchema(outputSchema);
  }

  @Override
  public void prepareRun(BatchSourceContext batchSourceContext) throws Exception {
    // Get failure collector for updated validation API
    FailureCollector collector = batchSourceContext.getFailureCollector();
    excelInputreaderConfig.validate(collector);
    collector.getOrThrowException();

    createDatasets(batchSourceContext);
    collector.getOrThrowException();

    Job job = JobUtils.createInstance();

    String processFiles = "";
    if (!Strings.isNullOrEmpty(excelInputreaderConfig.memoryTableName)) {
      processFiles = GSON.toJson(getAllProcessedFiles(batchSourceContext), ARRAYLIST_PREPROCESSED_FILES);
    }

    Map<String, String> arguments = new HashMap<>(batchSourceContext.getArguments().asMap());
    int byteArrayMaxOverride = arguments.containsKey(ExcelInputFormat.EXCEL_BYTE_ARRAY_MAX_OVERRIDE) ?
      Integer.parseInt(arguments.get(ExcelInputFormat.EXCEL_BYTE_ARRAY_MAX_OVERRIDE)) :
      ExcelInputFormat.EXCEL_BYTE_ARRAY_MAX_OVERRIDE_DEFAULT;

    ExcelInputFormat.setConfigurations(job, excelInputreaderConfig.filePattern, excelInputreaderConfig.sheet,
                                       excelInputreaderConfig.reprocess, excelInputreaderConfig.sheetValue,
                                       excelInputreaderConfig.columnList, excelInputreaderConfig.skipFirstRow,
                                       excelInputreaderConfig.terminateIfEmptyRow, excelInputreaderConfig.rowsLimit,
                                       excelInputreaderConfig.ifErrorRecord, processFiles, byteArrayMaxOverride);

    // Sets the input path(s).
    ExcelInputFormat.addInputPaths(job, excelInputreaderConfig.filePath);

    // Sets the filter based on extended class implementation.
    ExcelInputFormat.setInputPathFilter(job, ExcelReaderRegexFilter.class);
    SourceInputFormatProvider inputFormatProvider = new SourceInputFormatProvider(ExcelInputFormat.class,
                                                                                  job.getConfiguration());
    batchSourceContext.setInput(Input.of(excelInputreaderConfig.referenceName, inputFormatProvider));

    Schema schema = batchSourceContext.getOutputSchema();
    LineageRecorder lineageRecorder = new LineageRecorder(batchSourceContext, excelInputreaderConfig.referenceName);
    lineageRecorder.createExternalDataset(schema);

    if (schema != null && schema.getFields() != null) {
      lineageRecorder.recordRead("Read", "Read from Excel files.", TransformLineageRecorderUtils.getFields(schema));
    }

  }

  private void createDatasets(PipelineConfigurer pipelineConfigurer) {
    FailureCollector collector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    if (!excelInputreaderConfig.containsMacro("errorDatasetName") &&
      !Strings.isNullOrEmpty(excelInputreaderConfig.errorDatasetName)) {
      Map<String, String> properties = new HashMap<>();
      properties.put(Properties.Table.PROPERTY_SCHEMA, errorRecordSchema.toString());
      properties.put(Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, KEY);
      DatasetProperties datasetProperties = DatasetProperties.builder().addAll(properties).build();

      pipelineConfigurer.createDataset(excelInputreaderConfig.errorDatasetName, Table.class, datasetProperties);
    } else if (!excelInputreaderConfig.containsMacro("ifErrorRecord") &&
      excelInputreaderConfig.ifErrorRecord.equalsIgnoreCase(WRITE_ERROR_DATASET)) {
      collector.addFailure("Error dataset name should not be empty if choosing write to error "
                             + "dataset for 'On Error' input.", null)
        .withConfigProperty(IF_ERROR_RECORD).withConfigProperty(ERROR_DATASET_NAME);
    }

    if (!excelInputreaderConfig.containsMacro("memoryTableName") &&
      !Strings.isNullOrEmpty(excelInputreaderConfig.memoryTableName)) {
      pipelineConfigurer.createDataset(excelInputreaderConfig.memoryTableName, KeyValueTable.class);
    }
  }

  private void createDatasets(BatchSourceContext context) {
    FailureCollector collector = context.getFailureCollector();
    try {
      if (!excelInputreaderConfig.containsMacro("errorDatasetName") &&
        !Strings.isNullOrEmpty(excelInputreaderConfig.errorDatasetName)) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Properties.Table.PROPERTY_SCHEMA, errorRecordSchema.toString());
        properties.put(Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, KEY);
        DatasetProperties datasetProperties = DatasetProperties.builder().addAll(properties).build();

        if (context != null && !context.datasetExists(excelInputreaderConfig.errorDatasetName)) {
          context.createDataset(excelInputreaderConfig.errorDatasetName, Table.class.getName(), datasetProperties);
        }

      } else if (!excelInputreaderConfig.containsMacro("ifErrorRecord") &&
        excelInputreaderConfig.ifErrorRecord.equalsIgnoreCase(WRITE_ERROR_DATASET)) {
        collector.addFailure("Error dataset name should not be empty if choosing write to error "
                               + "dataset for 'On Error' input.", null)
          .withConfigProperty(IF_ERROR_RECORD).withConfigProperty(ERROR_DATASET_NAME);
        collector.getOrThrowException();
      }

      if (!excelInputreaderConfig.containsMacro("memoryTableName") &&
        !Strings.isNullOrEmpty(excelInputreaderConfig.memoryTableName)) {
        if (context != null && !context.datasetExists(excelInputreaderConfig.memoryTableName)) {
          context.createDataset(excelInputreaderConfig.memoryTableName, KeyValueTable.class.getName(),
                                DatasetProperties.EMPTY);
        }
      }
    } catch (DatasetManagementException e) {
      collector.addFailure(String.format("Exception while creating dataset: %s.", e.getMessage()), null);
    }
  }

  /**
   * Get the output schema from the Excel Input Reader specified by the user.
   */
  private void getOutputSchema() {
    if (outputSchema == null) {
      List<Schema.Field> outputFields = Lists.newArrayList();
      outputFields.add(Schema.Field.of(FILE, Schema.of(Schema.Type.STRING)));
      outputFields.add(Schema.Field.of(SHEET, Schema.of(Schema.Type.STRING)));
      try {
        // If processing of all the columns are required
        if (inputColumns == null || inputColumns.isEmpty()) {
          for (String fieldName : outputSchemaMapping.keySet()) {
            String columnName = fieldName;
            if (columnMapping.containsKey(fieldName)) {
              columnName = columnMapping.get(fieldName);
            }

            Schema fieldType = Schema.of(Schema.Type.valueOf(outputSchemaMapping.get(fieldName).toUpperCase()));
            outputFields.add(Schema.Field.of(columnName, fieldType));
          }
        } else {
          for (String column : inputColumns) {
            String columnName = column;
            if (columnMapping.containsKey(column)) {
              columnName = columnMapping.get(column);
            }
            if (outputSchemaMapping.containsKey(column)) {
              Schema fieldType = Schema.of(Schema.Type.valueOf(outputSchemaMapping.get(column).toUpperCase()));
              outputFields.add(Schema.Field.of(columnName, fieldType));
            } else {
              outputFields.add(Schema.Field.of(columnName, Schema.of(Schema.Type.STRING)));
            }

            outputFieldsMapping.put(column, columnName);
          }
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Exception while creating output schema for Excel input reader. " +
                                             "Invalid output " + "schema: " + e.getMessage(), e);
      }
      outputSchema = Schema.recordOf("outputSchema", outputFields);
    }
  }


  /**
   * Config class for ExcelInputReader.
   */
  public static class ExcelInputReaderConfig extends ReferencePluginConfig {

    @Name("filePath")
    @Description("Path of the excel file(s) to be read; for example: 'file:///home/cdap' for a " +
      "local path and 'hdfs://<namemode-hostname>:9000/cdap' for a path in hdfs.")
    @Macro
    private String filePath;

    @Name("filePattern")
    @Description("Regex pattern to select specific file(s); for example: '.*'")
    @Macro
    private String filePattern;

    @Nullable
    @Name("memoryTableName")
    @Description("KeyValue table name to keep the track of processed files. This can be a new table or existing one;" +
      " for example: 'inventory-memory-table'")
    private String memoryTableName;

    @Nullable
    @Description("Expiry period (days) for data in the table. Default is 30 days." +
      "Example - For tableExpiryPeriod = 30, data before 30 days get deleted from the table.")
    private String tableExpiryPeriod;

    @Name("reprocess")
    @Description("Specifies whether the file(s) should be reprocessed. " +
      "Options to select are true or false")
    private boolean reprocess;

    @Name("sheet")
    @Description("Specifies whether sheet has to be processed by sheet name or sheet no; " +
      "Shift 'Options are' in next line: " +
      "Sheet Name" +
      "Sheet Number")
    private String sheet;

    @Name("sheetValue")
    @Description("Specifies the value corresponding to 'sheet' input. Can be either sheet name or sheet no; " +
      "for example: 'Sheet1' or '0' in case user selects 'Sheet Name' or 'Sheet Number' as 'sheet' input " +
      "respectively. Sheet number starts with 0.")
    @Macro
    private String sheetValue;

    @Nullable
    @Name("columnList")
    @Description("Specify the excel column names which needs to be extracted from the excel sheet; for example: 'A,B'.")
    private String columnList;

    @Nullable
    @Name("columnMapping")
    @Description("List of the excel column name to be renamed. The key specifies the name of the column to rename, " +
      "with its corresponding value specifying the new name for that column names; for example A:id,B:name")
    private String columnMapping;

    @Name("skipFirstRow")
    @Description("Specify whether first row in the excel sheet is to be skipped or not. " +
      "Options to select are true or false.")
    private boolean skipFirstRow;

    @Name("terminateIfEmptyRow")
    @Description("Specify whether processing needs to be terminated in case an empty row is encountered " +
      "while processing excel files. Options to select are true or false.")
    private String terminateIfEmptyRow;

    @Nullable
    @Name("rowsLimit")
    @Description("Specify maximum number of rows to be processed for each sheet; All of the rows will be processed " +
      "if no limit is specified. for example: '100'.")
    @Macro
    private String rowsLimit;

    @Nullable
    @Name("outputSchema")
    @Description("Comma separated mapping of column names in the output schema to the data types;" +
      "for example: 'A:string,B:int'. This input is required if no inputs " +
      "for 'columnList' has been provided.")
    private String outputSchema;

    private String ifErrorRecord;

    @Nullable
    private String errorDatasetName;

    public ExcelInputReaderConfig() {
      super("ExcelInputReader");
    }

    public void validate(FailureCollector collector) {
      if (!containsMacro("sheetValue") && sheet.equalsIgnoreCase(SHEET_NO) && !StringUtils.isNumeric(sheetValue)) {
        collector.addFailure(
          String.format("Invalid sheet number: '%s'.", sheetValue),
          "The value should be greater than or equal to zero.")
          .withConfigProperty(SHEET_VALUE);
      }

      if (!(Strings.isNullOrEmpty(tableExpiryPeriod)) && (Strings.isNullOrEmpty(memoryTableName))) {
        collector.addFailure(
          "Value for Table Expiry Period is valid only when file tracking table is specified.", null)
          .withConfigProperty(TABLE_EXPIRY_PERIOD);
      }

      if (!Strings.isNullOrEmpty(rowsLimit) && !StringUtils.isNumeric(rowsLimit)) {
        collector.addFailure(String.format("Invalid row limit: '%s'.", rowsLimit), "Numeric value expected.")
          .withConfigProperty(ROWS_LIMIT);
      }

      if (Strings.isNullOrEmpty(columnList) &&
        Strings.isNullOrEmpty(outputSchema)) {
        collector.addFailure(
          "'Field Name Schema Type Mapping' and 'Columns To Be Extracted' cannot both be empty.", null)
          .withConfigProperty(OUTPUT_SCHEMA).withConfigProperty(COLUMN_LIST);
      }
    }
  }
}
