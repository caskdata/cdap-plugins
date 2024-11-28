@Send_Email
Feature: Send Email Alert - Run time scenarios

  @SEND_EMAIL @BQ_SINK_TEST @FILE_SOURCE_TEST
  Scenario Outline: Verify send email alert functionality using File to BigQuery pipeline for different run conditions
    Given Open Datafusion Project to configure pipeline
    And Select plugin: "File" from the plugins list as: "Source"
    And Expand Plugin group in the LHS plugins list: "Sink"
    And Select plugin: "BigQuery" from the plugins list as: "Sink"
    And Connect plugins: "File" and "BigQuery" to establish connection
    And Navigate to the properties page of plugin: "File"
    And Enter input plugin property: "referenceName" with value: "FileReferenceName"
    And Enter input plugin property: "path" with value: "<filepath>"
    And Select dropdown plugin property: "format" with option value: "csv"
    And Click plugin property: "skipHeader"
    And Click on the Get Schema button
    And Validate "File" plugin properties
    And Close the Plugin Properties page
    And Navigate to the properties page of plugin: "BigQuery"
    And Replace input plugin property: "projectId" with value: "projectId"
    And Enter input plugin property: "datasetProjectId" with value: "projectId"
    And Enter input plugin property: "referenceName" with value: "BQReferenceName"
    And Enter input plugin property: "dataset" with value: "dataset"
    And Enter input plugin property: "table" with value: "bqTargetTable"
    And Click plugin property: "truncateTable"
    And Click plugin property: "updateTableSchema"
    And Validate "BigQuery" plugin properties
    And Close the Plugin Properties page
    And Click plugin property: "configureTab"
    And Click plugin property: "pipelineAlertTab"
    And Click plugin property: "createAlertButton"
    And Select send email alert plugin
    And Select dropdown plugin property: "emailRunCondition" with option value: "<RunCondition>"
    And Enter input plugin property: "emailSender" with value: "sendEmailSender"
    And Enter send email plugin property email recipient with value: "sendEmailRecipients"
    And Enter input plugin property: "emailSubject" with value: "sendEmailSubject"
    And Enter textarea plugin property: "emailMessage" with value: "sendEmailMessage"
    And Enter input plugin property: "emailUsername" with value: "sendEmailUsername"
    And Enter input plugin property: "emailPassword" with value: "sendEmailPassword"
    And Enter input plugin property: "emailProtocol" with value: "sendEmailProtocol"
    And Enter input plugin property: "emailHost" with value: "sendEmailHost"
    And Enter input plugin property: "emailPort" with value: "sendEmailPort"
    And Validate send email plugin properties
    And Click plugin property: "emailNextButton"
    And Click plugin property: "emailConfirmButton"
    And Click Pipeline Alert Save Button
    And Save and Deploy Pipeline
    And Run the Pipeline in Runtime
    And Wait till pipeline is in running state
    And Open and capture logs
    And Verify the pipeline status is "<status>"
    #And Verify email sent message in logs (Implement this step once PLUGIN-1255 is fixed.)
    Examples:
      | RunCondition        | filepath                      | status    |
      | sendEmailCompletion | csvAllDataTypeFile            | Succeeded |
      | sendEmailSuccess    | csvAllDataTypeFile            | Succeeded |
      | sendEmailFailure    | sendEmailCsvInvalidFormatFile | Failed    |
      | sendEmailCompletion | sendEmailCsvInvalidFormatFile | Failed    |

  @SEND_EMAIL @BQ_SINK_TEST @FILE_SOURCE_TEST
  Scenario: Verify email sent failure text message in logs when the pipeline is successful
    Given Open Datafusion Project to configure pipeline
    And Select plugin: "File" from the plugins list as: "Source"
    And Expand Plugin group in the LHS plugins list: "Sink"
    And Select plugin: "BigQuery" from the plugins list as: "Sink"
    And Connect plugins: "File" and "BigQuery" to establish connection
    And Navigate to the properties page of plugin: "File"
    And Enter input plugin property: "referenceName" with value: "FileReferenceName"
    And Enter input plugin property: "path" with value: "csvAllDataTypeFile"
    And Select dropdown plugin property: "format" with option value: "csv"
    And Click plugin property: "skipHeader"
    And Click on the Get Schema button
    And Validate "File" plugin properties
    And Close the Plugin Properties page
    And Navigate to the properties page of plugin: "BigQuery"
    And Replace input plugin property: "projectId" with value: "projectId"
    And Enter input plugin property: "datasetProjectId" with value: "projectId"
    And Enter input plugin property: "referenceName" with value: "BQReferenceName"
    And Enter input plugin property: "dataset" with value: "dataset"
    And Enter input plugin property: "table" with value: "bqTargetTable"
    And Click plugin property: "truncateTable"
    And Click plugin property: "updateTableSchema"
    And Validate "BigQuery" plugin properties
    And Close the Plugin Properties page
    And Click plugin property: "configureTab"
    And Click plugin property: "pipelineAlertTab"
    And Click plugin property: "createAlertButton"
    And Select send email alert plugin
    And Select dropdown plugin property: "emailRunCondition" with option value: "sendEmailCompletion"
    And Enter input plugin property: "emailSender" with value: "sendEmailSender"
    And Enter send email plugin property email recipient with value: "sendEmailRecipients"
    And Enter input plugin property: "emailSubject" with value: "sendEmailSubject"
    And Enter textarea plugin property: "emailMessage" with value: "sendEmailMessage"
    And Enter input plugin property: "emailUsername" with value: "sendEmailUsername"
    And Enter input plugin property: "emailPassword" with value: "sendInvalidEmailPassword"
    And Enter input plugin property: "emailProtocol" with value: "sendEmailProtocol"
    And Enter input plugin property: "emailHost" with value: "sendEmailHost"
    And Enter input plugin property: "emailPort" with value: "sendEmailPort"
    And Validate send email plugin properties
    And Click plugin property: "emailNextButton"
    And Click plugin property: "emailConfirmButton"
    And Click Pipeline Alert Save Button
    And Save and Deploy Pipeline
    And Run the Pipeline in Runtime
    And Wait till pipeline is in running state
    And Open and capture logs
    And Verify the pipeline status is "Succeeded"
    And Close the pipeline logs
    And Open Pipeline logs and verify Log entries having below listed Level and Message:
      | Level | Message                      |
      | ERROR | errorMessageSentEmailFailure |
