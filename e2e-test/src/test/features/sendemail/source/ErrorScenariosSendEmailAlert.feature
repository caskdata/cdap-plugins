@Send_Email
Feature: Send Email Alert - Verify error scenarios

  @SEND_EMAIL-01
  Scenario: Verify send email alert validation errors for mandatory fields
    Given Open Datafusion Project to configure pipeline
    And Click plugin property: "configureTab"
    And Click plugin property: "pipelineAlertTab"
    And Click plugin property: "createAlertButton"
    And Select send email alert plugin
    And Click on the Validate button
    And Verify mandatory property error for below listed properties:
      | sender     |
      | recipients |
      | subject    |
      | message    |

  @SEND_EMAIL @PLUGIN-1253
  Scenario: Validate proper format for sender field in send email plugin
    Given Open Datafusion Project to configure pipeline
    And Click plugin property: "configureTab"
    And Click plugin property: "pipelineAlertTab"
    And Click plugin property: "createAlertButton"
    And Select send email alert plugin
    And Select dropdown plugin property: "emailRunCondition" with option value: "sendEmailCompletion"
    And Enter input plugin property: "emailSender" with value: "sendEmailInvalidSender"
    And Enter send email plugin property email recipient with value: "sendEmailRecipients"
    And Enter input plugin property: "emailSubject" with value: "sendEmailSubject"
    And Enter textarea plugin property: "emailMessage" with value: "sendEmailMessage"
    And Click on the Validate button
    And Verify that the Plugin Property: "sender" is displaying an in-line error message: "errorMessageInvalidSenderField"

  @SEND_EMAIL @PLUGIN-1253
  Scenario: Verify proper format for recipient field in send email plugin
    Given Open Datafusion Project to configure pipeline
    And Click plugin property: "configureTab"
    And Click plugin property: "pipelineAlertTab"
    And Click plugin property: "createAlertButton"
    And Select send email alert plugin
    And Select dropdown plugin property: "emailRunCondition" with option value: "sendEmailCompletion"
    And Enter input plugin property: "emailSender" with value: "sendEmailSender"
    And Enter send email plugin property email recipient with value: "sendEmailInvalidRecipients"
    And Enter input plugin property: "emailSubject" with value: "sendEmailSubject"
    And Enter textarea plugin property: "emailMessage" with value: "sendEmailMessage"
    And Click on the Validate button
    And Verify that the Plugin Property: "sender" is displaying an in-line error message: "errorMessageInvalidRecipientField"
