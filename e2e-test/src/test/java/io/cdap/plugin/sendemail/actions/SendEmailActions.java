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
package io.cdap.plugin.sendemail.actions;

import io.cdap.e2e.utils.ElementHelper;
import io.cdap.e2e.utils.PluginPropertyUtils;
import io.cdap.e2e.utils.SeleniumHelper;
import io.cdap.e2e.utils.WaitHelper;
import io.cdap.plugin.sendemail.locators.SendEmailLocators;


/**
 * Send-email alert related actions.
 */

public class SendEmailActions {

  static {
    SeleniumHelper.getPropertiesLocators(SendEmailLocators.class);
  }

  public static void clickOnSendEmail() {
    ElementHelper.clickOnElement(SendEmailLocators.sendEmailAlert);
  }

  public static void enterRecipientDetails(String value) {
    String valueFromPluginPropertiesFile = PluginPropertyUtils.pluginProp(value);
    if (valueFromPluginPropertiesFile == null) {
      ElementHelper.sendKeys(SendEmailLocators.recipientInput, value);
    } else {
      ElementHelper.sendKeys(SendEmailLocators.recipientInput, valueFromPluginPropertiesFile);
    }
  }

  public static void clickPipelineAlertSaveButton() {
    ElementHelper.clickOnElement(SendEmailLocators.pipelineAlertSaveButton);
  }

  public static void verifyIfSendEmailPluginIsValidatedSuccessfully() {
    WaitHelper.waitForElementToBeDisplayed(SendEmailLocators.sendEmailPluginValidationSuccessMsg);
  }
}
