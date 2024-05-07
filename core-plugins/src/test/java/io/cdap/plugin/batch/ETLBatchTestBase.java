/*
 * Copyright © 2015, 2016-2019 Cask Data, Inc.
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

package io.cdap.plugin.batch;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.cdap.cdap.api.artifact.ArtifactRange;
import io.cdap.cdap.api.artifact.ArtifactSummary;
import io.cdap.cdap.api.artifact.ArtifactVersion;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.TimePartitionedFileSet;
import io.cdap.cdap.common.utils.Tasks;
import io.cdap.cdap.datapipeline.DataPipelineApp;
import io.cdap.cdap.datapipeline.SmartWorkflow;
import io.cdap.cdap.etl.mock.test.HydratorTestBase;
import io.cdap.cdap.etl.proto.v2.ETLBatchConfig;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.ArtifactId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.test.ApplicationManager;
import io.cdap.cdap.test.WorkflowManager;
import io.cdap.plugin.alert.TMSAlertPublisher;
import io.cdap.plugin.batch.action.EmailAction;
import io.cdap.plugin.batch.action.SSHAction;
import io.cdap.plugin.batch.aggregator.DedupAggregator;
import io.cdap.plugin.batch.aggregator.GroupByAggregator;
import io.cdap.plugin.batch.aggregator.function.AggregateFunction;
import io.cdap.plugin.batch.joiner.Joiner;
import io.cdap.plugin.batch.sink.BatchCubeSink;
import io.cdap.plugin.batch.sink.FileSink;
import io.cdap.plugin.batch.sink.KVTableSink;
import io.cdap.plugin.batch.sink.SnapshotFileBatchAvroSink;
import io.cdap.plugin.batch.sink.SnapshotFileBatchParquetSink;
import io.cdap.plugin.batch.sink.TableSink;
import io.cdap.plugin.batch.sink.TimePartitionedFileSetDatasetAvroSink;
import io.cdap.plugin.batch.sink.TimePartitionedFileSetDatasetParquetSink;
import io.cdap.plugin.batch.source.FTPBatchSource;
import io.cdap.plugin.batch.source.KVTableSource;
import io.cdap.plugin.batch.source.SnapshotFileBatchAvroSource;
import io.cdap.plugin.batch.source.SnapshotFileBatchParquetSource;
import io.cdap.plugin.batch.source.TableSource;
import io.cdap.plugin.batch.source.TimePartitionedFileSetDatasetAvroSource;
import io.cdap.plugin.batch.source.TimePartitionedFileSetDatasetParquetSource;
import io.cdap.plugin.error.ErrorCollector;
import io.cdap.plugin.format.avro.input.AvroInputFormatProvider;
import io.cdap.plugin.format.avro.output.AvroOutputFormatProvider;
import io.cdap.plugin.format.blob.input.BlobInputFormatProvider;
import io.cdap.plugin.format.delimited.input.CSVInputFormatProvider;
import io.cdap.plugin.format.delimited.input.DelimitedInputFormatProvider;
import io.cdap.plugin.format.delimited.input.TSVInputFormatProvider;
import io.cdap.plugin.format.delimited.output.CSVOutputFormatProvider;
import io.cdap.plugin.format.delimited.output.DelimitedOutputFormatProvider;
import io.cdap.plugin.format.delimited.output.TSVOutputFormatProvider;
import io.cdap.plugin.format.json.input.JsonInputFormatProvider;
import io.cdap.plugin.format.json.output.JsonOutputFormatProvider;
import io.cdap.plugin.format.orc.output.OrcOutputFormatProvider;
import io.cdap.plugin.format.parquet.input.ParquetInputFormatProvider;
import io.cdap.plugin.format.parquet.output.ParquetOutputFormatProvider;
import io.cdap.plugin.format.text.input.TextInputFormatProvider;
import io.cdap.plugin.format.xls.input.XlsInputFormatProvider;
import io.cdap.plugin.transform.JavaScriptTransform;
import io.cdap.plugin.transform.ProjectionTransform;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;
import org.apache.orc.TypeDescription;
import org.apache.orc.mapred.OrcStruct;
import org.apache.orc.mapreduce.OrcOutputFormat;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.twill.filesystem.Location;
import org.junit.BeforeClass;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base test class that sets up plugin and the etl batch app artifacts.
 */
public class ETLBatchTestBase extends HydratorTestBase {

  protected static final ArtifactId DATAPIPELINE_ARTIFACT_ID =
    NamespaceId.DEFAULT.artifact("data-pipeline", "3.2.0");
  protected static final ArtifactSummary DATAPIPELINE_ARTIFACT = new ArtifactSummary("data-pipeline", "3.2.0");

  private static int startCount;

  @BeforeClass
  public static void setupTest() throws Exception {
    if (startCount++ > 0) {
      return;
    }

    setupBatchArtifacts(DATAPIPELINE_ARTIFACT_ID, DataPipelineApp.class);

    Set<ArtifactRange> parents = ImmutableSet.of(
      new ArtifactRange(NamespaceId.DEFAULT.getNamespace(), DATAPIPELINE_ARTIFACT_ID.getArtifact(),
                        new ArtifactVersion(DATAPIPELINE_ARTIFACT_ID.getVersion()), true,
                        new ArtifactVersion(DATAPIPELINE_ARTIFACT_ID.getVersion()), true)
    );
    // add artifact for batch sources and sinks
    addPluginArtifact(NamespaceId.DEFAULT.artifact("core-plugins", "1.0.0"), parents,
                      KVTableSource.class,
                      TableSource.class,
                      TimePartitionedFileSetDatasetAvroSource.class,
                      TimePartitionedFileSetDatasetParquetSource.class,
                      BatchCubeSink.class, KVTableSink.class, TableSink.class,
                      TimePartitionedFileSetDatasetAvroSink.class,
                      TimePartitionedFileSetDatasetParquetSink.class,
                      SnapshotFileBatchAvroSink.class, SnapshotFileBatchParquetSink.class,
                      SnapshotFileBatchAvroSource.class, SnapshotFileBatchParquetSource.class,
                      FTPBatchSource.class,
                      ProjectionTransform.class,
                      JavaScriptTransform.class,
                      GroupByAggregator.class,
                      DedupAggregator.class,
                      Joiner.class,
                      GroupByAggregator.class,
                      EmailAction.class,
                      SSHAction.class,
                      TMSAlertPublisher.class,
                      ErrorCollector.class,
                      FileSink.class,
                      // Spark needs this class to get exported to use the correct classloader
                      AggregateFunction.class);
    // add format plugins
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-avro", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(AvroOutputFormatProvider.PLUGIN_CLASS, AvroInputFormatProvider.PLUGIN_CLASS),
                      AvroOutputFormatProvider.class, AvroInputFormatProvider.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-blob", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(BlobInputFormatProvider.PLUGIN_CLASS), BlobInputFormatProvider.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-delimited", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(DelimitedOutputFormatProvider.PLUGIN_CLASS,
                                      DelimitedInputFormatProvider.PLUGIN_CLASS,
                                      CSVOutputFormatProvider.PLUGIN_CLASS, CSVInputFormatProvider.PLUGIN_CLASS,
                                      TSVOutputFormatProvider.PLUGIN_CLASS, TSVInputFormatProvider.PLUGIN_CLASS),
                      DelimitedOutputFormatProvider.class, DelimitedInputFormatProvider.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-json", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(JsonOutputFormatProvider.PLUGIN_CLASS, JsonInputFormatProvider.PLUGIN_CLASS),
                      JsonOutputFormatProvider.class, JsonInputFormatProvider.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-orc", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(OrcOutputFormatProvider.PLUGIN_CLASS),
                      OrcOutputFormatProvider.class, OrcOutputFormat.class, OrcStruct.class,
                      TypeDescription.class, TimestampColumnVector.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-parquet", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(ParquetOutputFormatProvider.PLUGIN_CLASS,
                                      ParquetInputFormatProvider.PLUGIN_CLASS),
                      ParquetOutputFormatProvider.class, ParquetInputFormatProvider.class,
                      Snappy.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-text", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
                      ImmutableSet.of(TextInputFormatProvider.PLUGIN_CLASS), TextInputFormatProvider.class);
    addPluginArtifact(NamespaceId.DEFAULT.artifact("formats-xls", "4.0.0"), DATAPIPELINE_ARTIFACT_ID,
            ImmutableSet.of(XlsInputFormatProvider.PLUGIN_CLASS), XlsInputFormatProvider.class);
  }

  protected List<GenericRecord> readOutput(TimePartitionedFileSet fileSet, Schema schema) throws IOException {
    List<GenericRecord> records = Lists.newArrayList();
    for (Location dayLoc : fileSet.getEmbeddedFileSet().getBaseLocation().list()) {
      // this level should be the day (ex: 2015-01-19)
      for (Location timeLoc : dayLoc.list()) {
        // this level should be the time (ex: 21-23.1234567890000)
        records.addAll(readOutput(timeLoc, schema));
      }
    }
    return records;
  }

  protected List<GenericRecord> readOutput(Location location, Schema schema) throws IOException {
    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(schema.toString());
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
    List<GenericRecord> records = Lists.newArrayList();
    for (Location file : location.list()) {
      // this level should be the actual mapred output
      String locName = file.getName();

      if (locName.endsWith(".avro")) {
        DataFileStream<GenericRecord> fileStream = new DataFileStream<>(file.getInputStream(), datumReader);
        Iterables.addAll(records, fileStream);
        fileStream.close();
      }
      if (locName.endsWith(".parquet")) {
        Path parquetFile = new Path(file.toString());
        AvroParquetReader.Builder<GenericRecord> genericRecordBuilder = AvroParquetReader.builder(parquetFile);
        ParquetReader<GenericRecord> reader = genericRecordBuilder.build();
        GenericRecord result = reader.read();
        while (result != null) {
          records.add(result);
          result = reader.read();
        }
      }
    }
    return records;
  }

  protected ApplicationManager deployETL(ETLBatchConfig etlConfig, String appName) throws Exception {
    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app(appName);
    return deployApplication(appId, appRequest);
  }

  /**
   * Run the SmartWorkflow in the given ETL application for once and wait for the workflow's COMPLETED status
   * with 5 minutes timeout.
   *
   * @param appManager the ETL application to run
   */
  protected WorkflowManager runETLOnce(ApplicationManager appManager)
    throws TimeoutException, InterruptedException, ExecutionException {
    return runETLOnce(appManager, ImmutableMap.of());
  }

  /**
   * Run the SmartWorkflow in the given ETL application for once and wait for the workflow's COMPLETED status
   * with 5 minutes timeout.
   *
   * @param appManager the ETL application to run
   * @param arguments the arguments to be passed when running SmartWorkflow
   */
  protected WorkflowManager runETLOnce(ApplicationManager appManager, Map<String, String> arguments)
    throws TimeoutException, InterruptedException, ExecutionException {
    final WorkflowManager workflowManager = appManager.getWorkflowManager(SmartWorkflow.NAME);
    int numRuns = workflowManager.getHistory().size();
    workflowManager.start(arguments);
    Tasks.waitFor(numRuns + 1, () -> workflowManager.getHistory().size(), 20, TimeUnit.SECONDS);
    workflowManager.waitForStopped(5, TimeUnit.MINUTES);
    return workflowManager;
  }
}
