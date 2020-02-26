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

package io.cdap.plugin.format.text.input;

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.plugin.format.input.PathTrackingInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.CombineFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.CombineFileRecordReader;
import org.apache.hadoop.mapreduce.lib.input.CombineFileRecordReaderWrapper;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Combined input format that tracks which file each text record was read from and optionally emits a file header
 * as the first record for each split.
 */
public class CombineTextInputFormat extends CombineFileInputFormat<NullWritable, StructuredRecord> {
  static final String HEADER = "combine.path.tracking.header";

  /**
   * Converts the CombineFileSplits derived by CombineFileInputFormat into CombineHeaderFileSplits
   * that optionally keep track of the header for each file.
   *
   * It is assumed that every file has the same header. It would be possible to read the header for each individual
   * file, but the use case for that is unclear and it could potentially add a decent amount of overhead if there
   * are a bunch of small files.
   */
  @Override
  public List<InputSplit> getSplits(JobContext job) throws IOException {
    ClassLoader cl = job.getConfiguration().getClassLoader();
    job.getConfiguration().setClassLoader(getClass().getClassLoader());
    List<InputSplit> fileSplits;
    try {
      fileSplits = super.getSplits(job);
    } finally {
      job.getConfiguration().setClassLoader(cl);
    }
    Configuration hConf = job.getConfiguration();

    boolean shouldCopyHeader = hConf.getBoolean(PathTrackingInputFormat.COPY_HEADER, false);
    List<InputSplit> splits = new ArrayList<>(fileSplits.size());

    String header = null;
    for (InputSplit split : fileSplits) {
      CombineFileSplit combineFileSplit = (CombineFileSplit) split;

      if (shouldCopyHeader && header == null) {
        header = getHeader(hConf, combineFileSplit);
      }
      splits.add(new CombineHeaderFileSplit(combineFileSplit, header));
    }

    return splits;
  }

  @Nullable
  private String getHeader(Configuration hConf, CombineFileSplit split) throws IOException {
    String header = null;
    for (Path path : split.getPaths()) {
      try (FileSystem fs = path.getFileSystem(hConf);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path), StandardCharsets.UTF_8))) {
        header = reader.readLine();
        if (header != null) {
          break;
        }
      }
    }
    return header;
  }

  /**
   * Creates a RecordReader that delegates to some other RecordReader for each path in the input split.
   * The header for each file is set in the context Configuration to make it available to the delegate RecordReaders.
   */
  @Override
  public RecordReader<NullWritable, StructuredRecord> createRecordReader(InputSplit split, TaskAttemptContext context)
    throws IOException {
    CombineHeaderFileSplit combineSplit = (CombineHeaderFileSplit) split;
    if (combineSplit.getHeader() != null) {
      context.getConfiguration().set(HEADER, combineSplit.getHeader());
    }
    return new CombineFileRecordReader<>(combineSplit, context, WrapperReader.class);
  }

  /**
   * A wrapper class that's responsible for delegating to a corresponding RecordReader in
   * {@link PathTrackingInputFormat}. All it does is pick the i'th path in the CombineFileSplit to create a
   * FileSplit and use the delegate RecordReader to read that split.
   */
  public static class WrapperReader extends CombineFileRecordReaderWrapper<NullWritable, StructuredRecord> {

    public WrapperReader(CombineFileSplit split, TaskAttemptContext context,
                         Integer idx) throws IOException, InterruptedException {
      super(new PathTrackingTextInputFormat(idx != 0), split, context, idx);
    }
  }
}
