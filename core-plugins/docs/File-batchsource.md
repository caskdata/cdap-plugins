# File Batch Source


Description
-----------
This source is used whenever you need to read from a distributed file system.
For example, you may want to read in log files from S3 every hour and then store
the logs in a TimePartitionedFileSet.


Properties
----------
**Reference Name:** Name used to uniquely identify this source for lineage, annotating metadata, etc.

**Path:** Path to read from. For example, s3a://<bucket>/path/to/input

**Format:** Format of the data to read.
The format must be one of 'avro', 'blob', 'csv', 'delimited', 'json', 'parquet', 'text', 'tsv', 'xls', or the
name of any format plugin that you have deployed to your environment.
If the format is a macro, only the pre-packaged formats can be used.
If the format is 'blob', every input file will be read into a separate record.
The 'blob' format also requires a schema that contains a field named 'body' of type 'bytes'.
If the format is 'text', the schema must contain a field named 'body' of type 'string'.

**Get Schema:** Auto-detects schema from file. Supported formats are: avro, parquet, csv, delimited, tsv, blob, xls
and text.

Blob - is set by default as field named 'body' of type bytes.

Text - is set by default as two fields: 'body' of type bytes and 'offset' of type 'long'.

JSON - is not supported, user has to manually provide the output schema.

Parquet - If the path is a directory, the plugin will look for files ending in '.parquet' to read the schema from. 
If no such file can be found, an error will be returned.

Avro - If the path is a directory, the plugin will look for files ending in '.avro' to read the schema from. 
If no such file can be found, an error will be returned.

**Override:** A list of columns with the corresponding data types for whom the automatic data type detection gets
 skipped. 
 
**Sample Size:** The maximum number of rows in a file that will get investigated for automatic data type detection.

**Terminate If Empty Row:** Specify whether to stop reading after encountering the first empty row. Defaults to false.

**Select Sheet Using:** Select the sheet by name or number. Default is 'Sheet Number'.

**Sheet Value:** The name/number of the sheet to read from. If not specified, the first sheet will be read.
Sheet Number are 0 based, ie first sheet is 0.

**Delimiter:** Delimiter to use when the format is 'delimited'. This will be ignored for other formats.

**Use First Row as Header:** Whether to use the first line of each file as the column headers. Supported formats are 'text', 'csv', 'tsv', 'xls', 'delimited'.

**Enable Quoted Values** Whether to treat content between quotes as a value. This value will only be used if the format
is 'csv', 'tsv' or 'delimited'. For example, if this is set to true, a line that looks like `1, "a, b, c"` will output two fields.
The first field will have `1` as its value and the second will have `a, b, c` as its value. The quote characters will be trimmed.
The newline delimiter cannot be within quotes.

It also assumes the quotes are well enclosed. The left quote will match the first following quote right before the delimiter. If there is an
unenclosed quote, an error will occur.

**Maximum Split Size:** Maximum size in bytes for each input partition.
Smaller partitions will increase the level of parallelism, but will require more resources and overhead.
The default value is 128MB.

**Regex Path Filter:** Regular expression that file paths must match in order to be included in the input.
The full file path is compared, not just the file name.
If no value is given, no file filtering will be done.
For example, a regex of .+\.csv will read only files that end in '.csv'.

See https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html for more information about 
the regular expression syntax

**Path Field:** Output field to place the path of the file that the record was read from.
If not specified, the file path will not be included in output records.
If specified, the field must exist in the output schema as a string.

**Path Filename Only:** Whether to only use the filename instead of the URI of the file path when a path field is given.
The default value is false.

**Read Files Recursively:** Whether files are to be read recursively from the path. The default value is false.

**Allow Empty Input:** Whether to allow an input path that contains no data. When set to false, the plugin
will error when there is no data to read. When set to true, no error will be thrown and zero records will be read.

**File System Properties:** Additional properties to use with the InputFormat when reading the data.
