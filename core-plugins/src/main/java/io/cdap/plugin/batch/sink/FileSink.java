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

package io.cdap.plugin.batch.sink;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.exception.ErrorCategory;
import io.cdap.cdap.api.exception.ErrorType;
import io.cdap.cdap.api.exception.ErrorUtils;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.batch.BatchSink;
import io.cdap.cdap.etl.api.batch.BatchSinkContext;
import io.cdap.plugin.batch.source.FileErrorDetailsProvider;
import io.cdap.plugin.format.plugin.AbstractFileSink;
import io.cdap.plugin.format.plugin.AbstractFileSinkConfig;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Writes to the FileSystem.
 */
@Plugin(type = BatchSink.PLUGIN_TYPE)
@Name("File")
@Description("Writes to the FileSystem.")
public class FileSink extends AbstractFileSink<FileSink.Conf> {
  private final Conf config;

  public FileSink(Conf config) {
    super(config);
    this.config = config;
  }

  @Override
  protected String getErrorDetailsProviderClassName() {
    return FileErrorDetailsProvider.class.getName();
  }

  @Override
  protected Map<String, String> getFileSystemProperties(BatchSinkContext context) {
    return config.getFSProperties();
  }

  /**
   * Config for File Sink.
   */
  public static class Conf extends AbstractFileSinkConfig {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final String NAME_FILE_SYSTEM_PROPERTIES = "fileSystemProperties";

    @Macro
    @Description("Destination path prefix. For example, 'hdfs://mycluster.net:8020/output'")
    private String path;

    @Macro
    @Nullable
    @Description("Advanced feature to specify any additional properties that should be used with the sink.")
    private String fileSystemProperties;

    private Conf() {
      fileSystemProperties = "{}";
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public void validate(FailureCollector collector) {
      super.validate(collector);
      try {
        getFSProperties();
      } catch (IllegalArgumentException e) {
        collector.addFailure(String.format("File system properties must be a valid json. %s: %s",
                e.getClass().getName(), e.getMessage()), null)
          .withConfigProperty(NAME_FILE_SYSTEM_PROPERTIES).withStacktrace(e.getStackTrace());
      }
    }

    private Map<String, String> getFSProperties() {
      if (fileSystemProperties == null || fileSystemProperties.isEmpty()) {
        return Collections.emptyMap();
      }
      try {
        return GSON.fromJson(fileSystemProperties, MAP_TYPE);
      } catch (JsonSyntaxException e) {
        String errorMessage = String.format(
            "Failed to parse filesystem properties %s with message: %s: %s", fileSystemProperties,
            e.getClass().getName(), e.getMessage());
        throw ErrorUtils.getProgramFailureException(
            new ErrorCategory(ErrorCategory.ErrorCategoryEnum.PLUGIN), errorMessage, errorMessage,
            ErrorType.USER, false, e);
      }
    }
  }
}
