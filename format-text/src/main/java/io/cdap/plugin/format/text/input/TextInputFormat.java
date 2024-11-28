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
import io.cdap.plugin.common.batch.JobUtils;
import io.cdap.plugin.format.input.PathTrackingInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.compress.CodecPool;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.Decompressor;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Input format that tracks which file each text record was read from and optionally emits a file header
 * as the first record for each split.
 */
public class TextInputFormat extends FileInputFormat<NullWritable, StructuredRecord> {

    /**
     * Converts the FileSplits derived by FileInputFormat into HeaderFileSplits
     * that optionally keep track of the header for each file.
     *
     * It is assumed that every file has a different header.
     */
    @Override
    public List<InputSplit> getSplits(JobContext job) throws IOException {
        List<InputSplit> fileSplits = JobUtils.applyWithExtraClassLoader(job, getClass().getClassLoader(),
                TextInputFormat.super::getSplits);

        Configuration hConf = job.getConfiguration();
        boolean shouldCopyHeader = hConf.getBoolean(PathTrackingInputFormat.COPY_HEADER, false);
        List<InputSplit> splits = new ArrayList<>(fileSplits.size());

        String header = null;
        for (InputSplit split : fileSplits) {
            FileSplit fileSplit = (FileSplit) split;
            if (shouldCopyHeader && header == null) {
                header = getHeader(hConf, fileSplit);
            }
            splits.add(new HeaderFileSplit(fileSplit, header));
        }

        return splits;
    }

    @Nullable
    private String getHeader(Configuration hConf, FileSplit split) throws IOException {
        String header = null;
        Path path = split.getPath();
        try (FileSystem fs = path.getFileSystem(hConf);
             BufferedReader reader = new BufferedReader(new InputStreamReader(openPath(hConf, fs, path),
                     StandardCharsets.UTF_8))) {
            header = reader.readLine();
        }

        return header;
    }

    /**
     * Opens the given {@link Path} for reading. It honors the compression codec if the file is compressed.
     */
    private InputStream openPath(Configuration hConf, FileSystem fs, Path path) throws IOException {
        CompressionCodec codec = new CompressionCodecFactory(hConf).getCodec(path);
        FSDataInputStream is = fs.open(path);
        if (codec == null) {
            return is;
        }
        Decompressor decompressor = CodecPool.getDecompressor(codec);
        return codec.createInputStream(is, decompressor);
    }

    @Override
    public RecordReader<NullWritable, StructuredRecord> createRecordReader(InputSplit split, TaskAttemptContext context)
            throws IOException, InterruptedException {
        HeaderFileSplit headerSplit = (HeaderFileSplit) split;
        if (headerSplit.getHeader() != null) {
            context.getConfiguration().set(PathTrackingTextInputFormat.HEADER, headerSplit.getHeader());
        }
        return (new PathTrackingTextInputFormat(false)).createRecordReader(split, context);
    }
}
