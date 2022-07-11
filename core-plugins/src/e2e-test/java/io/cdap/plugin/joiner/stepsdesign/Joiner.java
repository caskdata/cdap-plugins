/*
 * Copyright © 2022 Cask Data, Inc.
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
package io.cdap.plugin.joiner.stepsdesign;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageException;
import io.cdap.e2e.pages.locators.CdfPluginPropertiesLocators;
import io.cdap.e2e.utils.CdfHelper;
import io.cdap.e2e.utils.ElementHelper;
import io.cdap.e2e.utils.PluginPropertyUtils;
import io.cdap.e2e.utils.StorageClient;
import io.cdap.plugin.joiner.actions.JoinerActions;
import io.cucumber.java.en.Then;
import org.apache.directory.api.util.Strings;
import org.junit.Assert;
import stepsdesign.BeforeActions;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.cdap.plugin.common.stepsdesign.TestSetupHooks.gcsTargetBucketName;

/**
 *  Joiner Related Step Design.
 */
public class Joiner implements CdfHelper {
  @Then("Expand fields")
  public void expandFields() {
    JoinerActions.clickFieldsExpandButton();
  }

  @Then("Uncheck plugin {string} field {string} alias checkbox")
  public void uncheckPluginFieldAliasCheckBox(String plugin, String field) {
    JoinerActions.uncheckPluginFieldAliasCheckBox(plugin, field);
  }

  @Then("Enter numPartitions {int}")
  public void openJoinerProperties(int numPartitions) {
    JoinerActions.enterNumPartitions(String.valueOf(numPartitions));
  }

  @Then("Select joiner type {string}")
  public void selectJoinerType(String joinerType) {
    JoinerActions.selectJoinerType(joinerType);
  }

  @Then("Scroll to validation button and click")
  public void scrollToValidationButton() {
    ElementHelper.clickUsingActions(CdfPluginPropertiesLocators.validateButton);
  }

  @Then("Verify the CSV Output File matches the Expected Output File With Expected Partition")
  public void verifyCSVOutput() {
    try {
      // The output gcs folder will be like:
      // e2e-test-[uuid]
      // --2022-06-26-00-27/
      // ----_SUCCESS
      // ----part-r-0000
      // ----part-r-0001
      // ----part-r-...
      // The number of part-r-* files should match the expected partitions.
      int partitions = 0;
      List<String> lst = new ArrayList<>();
      for (Blob blob : StorageClient.listObjects(gcsTargetBucketName).iterateAll()) {
        String name = blob.getName();
        if (name.contains("part-r")) {
          partitions++;
          try (InputStream inputStream = new ByteArrayInputStream(blob.getContent())) {
            readInputStream(inputStream, lst);
          }
        }
      }
      Path path = Paths.get(Objects.requireNonNull(Joiner.class.getResource
        ("/" + PluginPropertyUtils.pluginProp("joinerOutput"))).getPath()).toAbsolutePath();
      Assert.assertEquals("Output partition should match",
                          partitions, Integer.parseInt(PluginPropertyUtils.pluginProp("expectedPartitions")));
      Assert.assertTrue("Output content should match",
                        Strings.equals(getSortedCSVContent(lst), new String(Files.readAllBytes(path))));
    } catch (StorageException | IOException e) {
      if (e.getMessage().contains("The specified bucket does not exist")) {
        BeforeActions.scenario.write("GCS Bucket " + gcsTargetBucketName + " does not exist.");
      } else {
        Assert.fail(e.getMessage());
      }
    }
  }
  private String getSortedCSVContent(List<String> lst) {
    // Since the spark output files aren't guaranteed to be ordered, the output entry with [id, first_name, last_name]
    // schema needs to be sorted by id for comparison purpose.
    lst.sort((s1, s2) -> {
      String id1 = s1.split(",")[0], id2 = s2.split(",")[0];
      return Integer.parseInt(id1) - Integer.parseInt(id2);
    });
    StringBuilder sb = new StringBuilder();
    for (String s : lst) {
      sb.append(s);
    }
    return sb.toString();
  }

  private void readInputStream(InputStream input, List<String> lst) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lst.add(line + "\n");
      }
    }
  }
}
