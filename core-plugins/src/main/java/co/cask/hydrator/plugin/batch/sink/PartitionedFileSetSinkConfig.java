/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.hydrator.plugin.batch.sink;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.plugin.PluginConfig;

import javax.annotation.Nullable;

/**
 * Abstract config for TimePartitionedFileSetSink
 */
public abstract class PartitionedFileSetSinkConfig extends PluginConfig {
  private static final String DEFAULT_FORMAT = "value";

  @Description("Name of the Partitioned FileSet Dataset to which the records " +
               "are written to. If it doesn't exist, it will be created.")
  protected String name;

  @Description("The base path for the Partitioned FileSet. Defaults to the " +
               "name of the dataset.")
  @Nullable
  protected String basePath;

  public PartitionedFileSetSinkConfig(String name, @Nullable String basePath) {
    this.name = name;
    this.basePath = basePath;
  }

  public void validate() {

  }
}
