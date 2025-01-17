/*
 * Copyright © 2015-2019 Cask Data, Inc.
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

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Metadata;
import io.cdap.cdap.api.annotation.MetadataProperty;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import io.cdap.cdap.etl.api.connector.Connector;
import io.cdap.plugin.batch.connector.FileConnector;
import io.cdap.plugin.format.input.PathTrackingInputFormat;
import io.cdap.plugin.format.plugin.AbstractFileSource;

import java.util.HashMap;
import java.util.Map;


/**
 * A {@link BatchSource} to use any distributed file system as a Source.
 */
@Plugin(type = "batchsource")
@Name(FileBatchSource.NAME)
@Description("Batch source for File Systems")
@Metadata(properties = {@MetadataProperty(key = Connector.PLUGIN_TYPE, value = FileConnector.NAME)})
public class FileBatchSource extends AbstractFileSource<FileSourceConfig> {
  public static final String NAME = "File";
  private final FileSourceConfig config;

  public FileBatchSource(FileSourceConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  protected String getErrorDetailsProviderClassName() {
    return FileErrorDetailsProvider.class.getName();
  }

  @Override
  protected Map<String, String> getFileSystemProperties(BatchSourceContext context) {
    Map<String, String> properties = new HashMap<>(config.getFileSystemProperties());
    if (config.shouldCopyHeader()) {
      properties.put(PathTrackingInputFormat.COPY_HEADER, "true");
    }
    if (config.getFileEncoding() != null
      && !config.getFileEncoding().equals(config.getDefaultFileEncoding())) {
      properties.put(PathTrackingInputFormat.SOURCE_FILE_ENCODING, config.getFileEncoding());
    }
    return properties;
  }

  @Override
  protected boolean shouldGetSchema() {
    return !config.containsMacro(FileSourceConfig.NAME_PATH) && !config.containsMacro(FileSourceConfig.NAME_FORMAT) &&
      !config.containsMacro(FileSourceConfig.NAME_DELIMITER) &&
      !config.containsMacro(FileSourceConfig.NAME_FILE_SYSTEM_PROPERTIES) &&
      !config.containsMacro(FileSourceConfig.NAME_FILE_ENCODING);
  }
}
