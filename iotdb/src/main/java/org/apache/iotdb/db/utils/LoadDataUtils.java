/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.filenode.FileNodeManager;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.metadata.ColumnSchema;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.apache.iotdb.tsfile.write.schema.FileSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * @author kangrong
 */
public class LoadDataUtils {

  private static Logger LOG = LoggerFactory.getLogger(LoadDataUtils.class);
  private BufferedReader inputCsvFileReader;
  private BufferedWriter extraDataFileWriter;
  private FileSchema fileSchema;
  private Set<String> writeInstanceMap;
  private MManager mmanager;
  private int writeInstanceThreshold;
  private boolean hasExtra = false;
  private long totalPointCount = 0;
  private FileNodeManager fileNodeManager;
  private IoTDBConfig conf = IoTDBDescriptor.getInstance().getConfig();

  /**
   * Constructor of LoadDataUtils.
   */
  public LoadDataUtils() {
    writeInstanceMap = new HashSet<>();
    fileNodeManager = FileNodeManager.getInstance();
    writeInstanceThreshold = conf.writeInstanceThreshold;
  }

  /**
   * function for loading local data in one pass.
   *
   * @param inputCsvDataPath seriesPath
   * @return extra data file in this circle as input csv seriesPath in next circle
   */
  private String loadLocalDataOnePass(String inputCsvDataPath) {
    hasExtra = false;
    // prepare file for extra data
    String extraDataFilePath = prepareFilePathAddOne(inputCsvDataPath);
    File extraDataFile = new File(extraDataFilePath);
    try {
      this.extraDataFileWriter = new BufferedWriter(new FileWriter(extraDataFile));
    } catch (IOException e) {
      LOG.error("create");
      e.printStackTrace();
      close();
      return null;
    }
    // prepare input csv data file.
    try {
      this.inputCsvFileReader = new BufferedReader(new FileReader(inputCsvDataPath));
    } catch (FileNotFoundException e1) {
      LOG.error("inputCsvDataPath:{} not found!", inputCsvDataPath);
      close();
      return null;
    }
    // load data for each line
    long lineCount = 0;
    final long startTime = System.currentTimeMillis();
    long temp = System.currentTimeMillis();
    String line;
    try {
      while ((line = inputCsvFileReader.readLine()) != null) {
        if (line.trim().equals("")) {
          continue;
        }
        if (lineCount % 1000000 == 0) {
          long endTime = System.currentTimeMillis();
          LOG.info("write line:{}, use time:{}", lineCount, (endTime - temp));
          temp = System.currentTimeMillis();
          LOG.info("load data points:{}, load data speed:{}w point/s", totalPointCount,
              FileUtils.format(((float) totalPointCount / 10) / (endTime - startTime), 2));
        }
        loadOneRecordLine(line);
        lineCount++;
      }
    } catch (IOException e1) {
      LOG.error("read line from inputCsvFileReader failed:{}", inputCsvDataPath);
      extraDataFilePath = null;
    } finally {
      LOG.info("write line:{}", lineCount);
      close();
      closeWriteInstance();
    }
    return extraDataFilePath;
  }

  private void loadOneRecordLine(String line) {
    TSRecord record = RecordUtils.parseSimpleTupleRecord(line, this.fileSchema);
    totalPointCount += record.dataPointList.size();
    String nsPath = null;
    try {
      nsPath = mmanager.getFileNameByPath(record.deviceId);
    } catch (PathErrorException e) {
      LOG.error("given seriesPath not found.{}", e.getMessage());
    }
    if (!writeInstanceMap.contains(nsPath)) {
      if (writeInstanceMap.size() < writeInstanceThreshold) {
        writeInstanceMap.add(nsPath);
      } else {
        hasExtra = true;
        try {
          extraDataFileWriter.write(line);
          extraDataFileWriter.newLine();
        } catch (IOException e) {
          LOG.error("record the extra data into extraFile failed, record:{}", line);
        }
        return;
      }
    }
    // appeared before, insert directly
    try {
      fileNodeManager.insert(record, false);
    } catch (FileNodeManagerException e) {
      LOG.error("failed when insert into fileNodeManager, record:{}, reason:{}", line,
          e.getMessage());
    }
  }

  private String prepareFilePathAddOne(String srcFilePath) {
    String extraExt = "deltaTempExt";
    int srcEnd = srcFilePath.indexOf(extraExt);
    if (srcEnd != -1) {
      srcFilePath = srcFilePath.substring(0, srcEnd);
    }
    File file;
    int ext = 0;
    String tempFile = srcFilePath;
    while (true) {
      file = new File(tempFile);
      if (file.exists()) {
        tempFile = srcFilePath + extraExt + (ext++);
      } else {
        break;
      }
    }
    return tempFile;
  }

  private void close() {
    try {
      if (inputCsvFileReader != null) {
        inputCsvFileReader.close();
      }
      if (extraDataFileWriter != null) {
        extraDataFileWriter.close();
      }
    } catch (IOException e) {
      LOG.error("close inputCsvFileReader and extraDataFileWriter failed");
    }
  }

  private void closeWriteInstance() {
    writeInstanceMap.clear();
  }

  /**
   * Constructor for loading local data in multiple pass.
   */
  public void loadLocalDataMultiPass(String inputCsvDataPath, String measureType, MManager mmanager)
      throws ProcessorException {
    checkIfFileExist(inputCsvDataPath);
    LOG.info("start loading data...");
    long startTime = System.currentTimeMillis();
    this.mmanager = mmanager;
    // get measurement schema
    try {
      ArrayList<ColumnSchema> meaSchema = mmanager.getSchemaForOneType(measureType);
      fileSchema = FileSchemaUtils.getFileSchemaFromColumnSchema(meaSchema, measureType);
    } catch (PathErrorException e) {
      LOG.error("the seriesPath of input measurement schema meet error!", e);
      close();
      return;
    } catch (WriteProcessException e) {
      e.printStackTrace();
    }
    String extraPath = inputCsvDataPath;
    List<String> extraPaths = new ArrayList<>();
    do {
      LOG.info("cycle: write csv file: {}", extraPath);
      extraPath = loadLocalDataOnePass(extraPath);
      extraPaths.add(extraPath);
    } while (hasExtra);
    for (String ext : extraPaths) {
      try {
        org.apache.commons.io.FileUtils.forceDelete(new File(ext));
        LOG.info("delete old file:{}", ext);
      } catch (IOException e) {
        LOG.error("fail to delete extra file {}", ext, e);
      }
    }
    long endTime = System.currentTimeMillis();
    LOG.info("load data successfully! total data points:{}, load data speed:{}w point/s",
        totalPointCount,
        FileUtils.format(((float) totalPointCount / 10) / (endTime - startTime), 2));
  }

  // add by XuYi on 2017/7/17
  private void checkIfFileExist(String filePath) throws ProcessorException {
    File file = new File(filePath);
    if (!file.exists()) {
      throw new ProcessorException(String.format("input file %s does not exist", filePath));
    }
  }
}
