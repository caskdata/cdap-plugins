@File_Sink
Feature:File Sink - Verify File Sink Plugin Error scenarios

  Scenario:Verify File sink plugin validation errors for mandatory fields
    Given Open Datafusion Project to configure pipeline
    When Expand Plugin group in the LHS plugins list: "Sink"
    When Select plugin: "File" from the plugins list as: "Sink"
    Then Navigate to the properties page of plugin: "File"
    Then Click on the Validate button
    Then Verify mandatory property error for below listed properties:
      | path          |
      | format        |
