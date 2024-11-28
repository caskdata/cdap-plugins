#
# Copyright © 2022 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#

@Joiner
Feature: Joiner analytics - Verify File source data transfer using Joiner analytics
  @GCS_SOURCE_TEST @GCS_SOURCE_JOINER_TEST @GCS_SINK_TEST
  Scenario: To verify data is getting transferred from File source to File sink plugin successfully with Joiner
    Given Open Datafusion Project to configure pipeline
    When Select plugin: "GCS" from the plugins list as: "Source"
    When Select plugin: "GCS" from the plugins list as: "Source"
    Then Move plugins: "GCS2" by xOffset 0 and yOffset 200
    When Expand Plugin group in the LHS plugins list: "Analytics"
    When Select plugin: "Joiner" from the plugins list as: "Analytics"
    Then Connect plugins: "GCS" and "Joiner" to establish connection
    Then Connect plugins: "GCS2" and "Joiner" to establish connection
    When Expand Plugin group in the LHS plugins list: "Sink"
    When Select plugin: "GCS" from the plugins list as: "Sink"
    Then Connect plugins: "Joiner" and "GCS3" to establish connection
    Then Navigate to the properties page of plugin: "GCS"
    Then Enter input plugin property: "referenceName" with value: "firstName"
    Then Enter input plugin property: "path" with value: "gcsSourceBucket1"
    Then Select dropdown plugin property: "format" with option value: "csv"
    Then Click plugin property: "skipHeader"
    Then Click plugin property: "enableQuotedValues"
    Then Click on the Get Schema button
    Then Verify the Output Schema matches the Expected Schema: "firstNameOutputSchema"
    Then Validate "GCS" plugin properties
    Then Close the Plugin Properties page
    Then Navigate to the properties page of plugin: "GCS2"
    Then Enter input plugin property: "referenceName" with value: "lastName"
    Then Enter input plugin property: "path" with value: "gcsSourceBucket2"
    Then Select dropdown plugin property: "format" with option value: "csv"
    Then Click plugin property: "skipHeader"
    Then Click plugin property: "enableQuotedValues"
    Then Click on the Get Schema button
    Then Verify the Output Schema matches the Expected Schema: "lastNameOutputSchema"
    Then Validate "GCS" plugin properties
    Then Close the Plugin Properties page
    Then Navigate to the properties page of plugin: "Joiner"
    Then Expand fields
    Then Uncheck plugin "GCS2" field "id" alias checkbox
    Then Select joiner type "Inner"
    Then Enter numPartitions "expectedJoinerOutputPartitions"
    Then Select dropdown plugin property: "inputsToLoadMemory" with option value: "GCS"
    Then Scroll to validation button and click
    Then Validate "Joiner" plugin properties
    Then Close the Plugin Properties page
    Then Navigate to the properties page of plugin: "GCS3"
    Then Enter input plugin property: "referenceName" with value: "result"
    Then Enter input plugin property: "path" with value: "gcsTargetBucket"
    Then Select dropdown plugin property: "format" with option value: "csv"
    Then Close the Plugin Properties page
    Then Save the pipeline
    Then Preview and run the pipeline
    Then Wait till pipeline preview is in running state
    Then Open and capture pipeline preview logs
    Then Verify the preview run status of pipeline in the logs is "succeeded"
    Then Close the pipeline logs
    Then Close the preview
    Then Deploy the pipeline
    Then Run the Pipeline in Runtime
    Then Wait till pipeline is in running state
    Then Open and capture logs
    Then Verify the pipeline status is "Succeeded"
    Then Verify the CSV Output File matches the Expected Output File: "joinerOutput" With Expected Partitions: "expectedJoinerOutputPartitions"
    Then Close the pipeline logs
