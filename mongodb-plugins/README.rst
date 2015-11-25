=========================================
MongoDB Source and Sink Plugin Collection
=========================================

Introduction
============

This project is a collection of MongoDB source and sink plugins. Following is list of plugins that are currently available:

- MongoDB Batch Source,
- MongoDB Batch Sink,
- MongoDB RealtimeSink

Getting Started
===============

Following are instructions to build and deploy hydrator MongoDB plugins.

Prerequisites
-------------

To use plugins, you must have CDAP version 3.2.0 or later. You can download CDAP Standalone that includes Hydrator `here <http://cask.co/downloads>`__

Build Plugins
-------------

You can get started with Hydrator plugins by building directly from the latest source code::

  git clone https://github.com/caskdata/hydrator-plugins.git
  cd hydrator-plugins
  mvn clean package -pl mongodb-plugins

After the build completes, you will have a jar for each plugin under the
``mongodb-plugins/target/`` directory.

Deploy Plugins
--------------

You can deploy transform plugins using the CDAP CLI::

  > load artifact target/mongodb-plugins-1.1.0-SNAPSHOT-batch.jar \
         config-file resources/plugin/mongodb-batch-plugins.json

  > load artifact target/mongodb-plugins-1.1.0-SNAPSHOT-realtime.jar \
         config-file resources/plugin/mongodb-realtime-plugins.json

Copy the UI configuration to CDAP installation::

  > cp mongodb-plugins/resources/ui/*.json $CDAP_HOME/ui/templates/common/

Plugin Descriptions
===================

MongoDB Batch Source
--------------------

:Id:
    **MongoDB**
:Type:
    batchsource
:Mode:
    Batch
:Description:
    Reads documents from MongoDB collection and converts each document into a StructuredRecord with the help
    of the specified schema. The user can optionally provide input query, input fields, and splitter class.
:Configuration:
    **connectionString:** MongoDB Connection String
    **schema:** Specifies the schema of document.

    **Optional Fields**

    **authConnectionString:** Auxiliary MongoDB connection string
    **inputQuery:** Filter the input collection with a query
    **inputFields:** Projection document that can limit the fields that appear in each document
    **splitterClass:** Name of the Splitter Class to use

MongoDB Batch Sink
------------------

:Id:
    **MongoDB**
:Type:
    batchsink
:Mode:
    Batch
:Description:
    Converts a StructuredRecord to a BSONWritable and then writes it to a MongoDB collection.
:Configuration:
    **connectionString:** MongoDB Connection string.

MongoDB Realtime Sink
---------------------

:Id:
    **MongoDB**
:Type:
    realtimesink
:Mode:
    Realtime
:Description:
    Takes a StructuredRecord from the previous stage, converts it to a BSONDocument and writes to MongoDB
:Configuration:
    **connectionString:** MongoDB Connection string.
    **dbName:** Mongo DB Database Name
    **collectionName:** Mongo DB Collection Name

License and Trademarks
======================

Copyright © 2015 Cask Data, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the License.

Cask is a trademark of Cask Data, Inc. All rights reserved.

Apache, Apache HBase, and HBase are trademarks of The Apache Software Foundation. Used with
permission. No endorsement by The Apache Software Foundation is implied by the use of these marks.
