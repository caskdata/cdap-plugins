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

package co.cask.plugin.etl.realtime;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.plugin.PluginConfig;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.realtime.DataWriter;
import co.cask.cdap.etl.api.realtime.RealtimeContext;
import co.cask.cdap.etl.api.realtime.RealtimeSink;
import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@link RealtimeSink} that writes data to a Cassandra server.
 * <p>
 * This {@link RealtimeCassandraSink} takes in a {@link StructuredRecord},
 * and writes it to the Cassandra server.
 * </p>
 */
@Plugin(type = "realtimesink")
@Name("Cassandra")
@Description("CDAP Cassandra Realtime Sink takes the structured record from the input source and converts it " +
  "to a CQL query, then inserts it in Cassandra using the keyspace and column family specified by the user. " +
  "The Cassandra server should be running prior to creating the adapter.")
public class RealtimeCassandraSink extends RealtimeSink<StructuredRecord> {
  private final RealtimeCassandraSinkConfig config;

  private Cluster cluster;
  private Session session;

  public RealtimeCassandraSink(RealtimeCassandraSinkConfig config) {
    this.config = config;
  }

  @Override
  public void initialize(RealtimeContext context) {
    Collection<InetSocketAddress> addresses = new ArrayList<>();

    for (String address : config.addresses.split(",")) {
      addresses.add(new InetSocketAddress(address.split(":")[0],
                                          Integer.valueOf(address.split(":")[1])));
    }
    Cluster.Builder builder = new Cluster.Builder().addContactPointsWithPorts(addresses);
    if (!Strings.isNullOrEmpty(config.username)) {
      builder.withCredentials(config.username, config.password);
    }
    builder.withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.valueOf(config.consistencyLevel)));
    builder.withCompression(ProtocolOptions.Compression.valueOf(config.compression));
    cluster = builder.build();
    session = cluster.connect(config.keyspace);
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    super.configurePipeline(pipelineConfigurer);
    Preconditions.checkArgument(!(Strings.isNullOrEmpty(config.username) ^ Strings.isNullOrEmpty(config.password)),
                                "You must either set both username and password or neither username or password. " +
                                  "Currently, they are username: " + config.username +
                                  " and password: " + config.password);
  }

  @Override
  public int write(Iterable<StructuredRecord> structuredRecords, DataWriter dataWriter) throws Exception {
    List<String> columns = Arrays.asList(config.columns.split(","));
    PreparedStatement statement = session.prepare(String.format("INSERT INTO %s (%s) VALUES (%s)",
                                                                config.columnFamily,
                                                                config.columns.replaceAll(",", ", "),
                                                                config.columns.replaceAll("[^,]+", "?")
                                                                  .replaceAll(",", ", ")));
    BatchStatement batch = new BatchStatement();
    int count = 0;
    for (StructuredRecord record : structuredRecords) {
      Object[] toBind = new Object[columns.size()];
      for (int i = 0; i < columns.size(); i++) {
        toBind[i] = record.get(columns.get(i));
      }
      batch.add(statement.bind(toBind));
      count++;
    }
    session.execute(batch);
    return count;
  }

  @Override
  public void destroy() {
    cluster.close();
  }

  /**
   * Config class for Realtime Cassandra Source
   */
  public static class RealtimeCassandraSinkConfig extends PluginConfig {
    @Name(Cassandra.COLUMN_FAMILY)
    @Description("The column family to inject data into. Create the column family before starting the adapter.")
    private String columnFamily;

    @Name(Cassandra.KEYSPACE)
    @Description("The keyspace to inject data into. Create the keyspace before starting the adapter.")
    private String keyspace;

    @Name(Cassandra.ADDRESSES)
    @Description("A comma-separated list of address(es) to connect to. For example, \"host1:9042,host2:9042\".")
    private String addresses;

    @Name(Cassandra.USERNAME)
    @Description("The username for the keyspace (if one exists). " +
      "If this is nonempty, then you must also supply a password")
    @Nullable
    private String username;

    @Name(Cassandra.PASSWORD)
    @Description("The password for the keyspace (if one exists). " +
      "If this is nonempty, then you must also supply a username")
    @Nullable
    private String password;

    @Name(Cassandra.COLUMNS)
    @Description("A comma-separated list of columns in the column family. " +
      "The columns should be listed in the same order as they are stored in the column family.")
    private String columns;

    @Name(Cassandra.CONSISTENCY_LEVEL)
    @Description("The string representation of the consistency level for the query. For example: \"QUORUM\"")
    private String consistencyLevel;

    @Name(Cassandra.COMPRESSION)
    @Description("The string representation of the compression for the query. For example: \"NONE\"")
    private String compression;

    public RealtimeCassandraSinkConfig(String columnFamily, String columns, String compression,
                                       String keyspace, String addresses, String consistencyLevel,
                                       @Nullable String username, @Nullable String password) {
      this.addresses = addresses;
      this.columnFamily = columnFamily;
      this.keyspace = keyspace;
      this.username = username;
      this.password = password;
      this.consistencyLevel = consistencyLevel;
      this.columns = columns;
      this.compression = compression;
    }
  }

  /**
   * Properties for Cassandra
   */
  public static class Cassandra {
    public static final String COLUMN_FAMILY = "columnFamily";
    public static final String KEYSPACE = "keyspace";
    public static final String ADDRESSES = "addresses";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String COLUMNS = "columns";
    public static final String CONSISTENCY_LEVEL = "consistencyLevel";
    public static final String COMPRESSION = "compression";
  }
}

