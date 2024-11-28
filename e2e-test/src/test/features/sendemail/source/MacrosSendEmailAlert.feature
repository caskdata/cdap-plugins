@Send_Email
Feature: Send Email Alert - Verify macro scenarios

  @SEND_EMAIL @FILE_SOURCE_TEST @BQ_SINK_TEST
  Scenario: Verify send email functionality with macro arguments using File to BigQuery pipeline
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
    And Verify the Output Schema matches the Expected Schema: "csvAllDataTypeFileSchema"
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
    And Click on the Macro button of Property: "emailSender" and set the value to: "sender"
    And Click on the Macro button of Property: "emailRecipient" and set the value to: "recipients"
    And Click on the Macro button of Property: "emailSubject" and set the value to: "subject"
    And Click on the Macro button of Property: "emailMessage" and set the value in textarea: "message"
    And Click on the Macro button of Property: "emailUsername" and set the value to: "username"
    And Click on the Macro button of Property: "emailPassword" and set the value to: "password"
    And Click on the Macro button of Property: "emailProtocol" and set the value to: "protocol"
    And Click on the Macro button of Property: "emailHost" and set the value to: "host"
    And Click on the Macro button of Property: "emailPort" and set the value to: "port"
    And Click on the Macro button of Property: "emailIncludeWorkflowToken" and set the value to: "token"
    And Validate send email plugin properties
    And Click plugin property: "emailNextButton"
    And Click plugin property: "emailConfirmButton"
    And Click Pipeline Alert Save Button
    And Save and Deploy Pipeline
    And Run the Pipeline in Runtime
    And Enter runtime argument value "sendEmailSender" for key "sender"
    And Enter runtime argument value "sendEmailRecipients" for key "recipients"
    And Enter runtime argument value "emailSubject" for key "subject"
    And Enter runtime argument value "sendEmailMessage" for key "message"
    And Enter runtime argument value "sendEmailUsername" for key "username"
    And Enter runtime argument value "sendEmailPassword" for key "password"
    And Enter runtime argument value "sendEmailProtocol" for key "protocol"
    And Enter runtime argument value "sendEmailHost" for key "host"
    And Enter runtime argument value "sendEmailPort" for key "port"
    And Enter runtime argument value "sendEmailIncludeWorkflowToken" for key "token"
    And Run the Pipeline in Runtime with runtime arguments
    And Wait till pipeline is in running state
    And Open and capture logs
    And Verify the pipeline status is "Succeeded"
    #And Verify email sent message in logs (Implement this step once PLUGIN-1255 is fixed.)
    Then Close the pipeline logs
    Then Validate OUT record count is equal to IN record count
