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
package io.cdap.plugin.sendemail.stepsdesign;

import io.cdap.e2e.pages.actions.CdfPluginPropertiesActions;
import io.cdap.plugin.sendemail.actions.SendEmailActions;
import io.cucumber.java.en.And;

/**
 * Send-email alert plugin related step design.
 */

public class SendEmail {

  @And("Select send email alert plugin")
  public void selectSendEmailAlert() {
    SendEmailActions.clickOnSendEmail();
  }

  @And("Enter send email plugin property email recipient with value: {string}")
  public void enterSendEmailPluginPropertyEmailRecipientWithValue(String value) {
    SendEmailActions.enterRecipientDetails(value);
  }

  @And("Click Pipeline Alert Save Button")
  public void clickPipelineAlertSaveButton() {
    SendEmailActions.clickPipelineAlertSaveButton();
  }

  @And("Validate send email plugin properties")
  public void validateSendEmailPluginProperties() {
    CdfPluginPropertiesActions.clickValidateButton();
    SendEmailActions.verifyIfSendEmailPluginIsValidatedSuccessfully();
  }

  @And("Verify email sent message in logs")
  public void verifyEmailSentMessageInLogs() {
    // (Implement this step once PLUGIN-1255 is fixed.)
  }
}
