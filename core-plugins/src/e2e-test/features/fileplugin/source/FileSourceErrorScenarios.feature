@File_Source
Feature:File Source - Verify File Source Plugin Error scenarios

  @File_Source_Required
  Scenario:Verify File source plugin validation errors for mandatory fields
    Given Open Datafusion Project to configure pipeline
    When Select plugin: "File" from the plugins list as: "Source"
    Then Navigate to the properties page of plugin: "File"
    Then Click on the Validate button
    Then Verify mandatory property error for below listed properties:
      | path          |
      | format        |

  Scenario:Verify File source plugin error for invalid input path
    Given Open Datafusion Project to configure pipeline
    When Select plugin: "File" from the plugins list as: "Source"
    Then Navigate to the properties page of plugin: "File"
    Then Enter input plugin property: "referenceName" with value: "FileReferenceName"
    Then Enter input plugin property: "path" with value: "incorrectFilePath"
    Then Select dropdown plugin property: "format" with option value: "csv"
    Then Click on the Get Schema button
    Then Verify that the Plugin is displaying an error message: "errorMessageInputPath" on the header
