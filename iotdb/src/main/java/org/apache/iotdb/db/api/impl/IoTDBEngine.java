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
package org.apache.iotdb.db.api.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import org.apache.iotdb.db.api.ITSEngine;
import org.apache.iotdb.db.api.IoTDBEngineException;
import org.apache.iotdb.db.api.IoTDBOptions;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.filenode.FileNodeManager;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.FileNodeProcessorException;
import org.apache.iotdb.db.exception.MetadataArgsErrorException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.exception.RecoverException;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.qp.QueryProcessor;
import org.apache.iotdb.db.qp.executor.OverflowQPExecutor;
import org.apache.iotdb.db.query.control.FileReaderManager;
import org.apache.iotdb.db.query.control.QueryTokenManager;
import org.apache.iotdb.db.service.CloseMergeService;
import org.apache.iotdb.db.writelog.manager.MultiFileLogNodeManager;
import org.apache.iotdb.db.writelog.manager.WriteLogNodeManager;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTDBEngine implements ITSEngine {

  private class DBLock {

    private File lockFile = null;
    private FileChannel fileChannel;
    private FileLock fileLock = null;

    private DBLock(File file) throws IOException {
      file.mkdirs();
      this.lockFile = new File(file, "LOCK");
      this.fileChannel = (new RandomAccessFile(lockFile, "rw")).getChannel();
      try {
        this.fileLock = this.fileChannel.tryLock();
      } catch (IOException e) {
        fileChannel.close();
      }
      if (this.fileLock == null) {
        throw new IOException(String
            .format("Unable to acquire lock on \'%s\'", new Object[]{lockFile.getAbsolutePath()}));
      }
    }

    public void release() {
      try {
        this.fileLock.release();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        try {
          this.fileChannel.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(IoTDBEngine.class);

  private File file;
  private DBLock lock;
  private IoTDBOptions options;
  private MManager mManager;
  private IoTDBConfig ioTDBConfig;
  private FileNodeManager fileNodeManager;
  private OverflowQPExecutor overflowQPExecutor;
  private QueryProcessor queryProcessor;


  /**
   * Get a IoTDBEngine
   *
   * @param file the location of the IoTDBEngine
   * @param ioTDBOptions some configuration for this IoTDBEngine
   */
  public IoTDBEngine(File file, IoTDBOptions ioTDBOptions) {
    this.file = file;
    this.options = ioTDBOptions;
  }

  @Override
  public void openOrCreate() throws IoTDBEngineException {
    // Add lock
    try {
      this.lock = new DBLock(this.file);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IoTDBEngineException(e);
    }
    // 修改配置信息以及更新配置信息
    ioTDBConfig = IoTDBDescriptor.getInstance().getConfig();
    // data 目录的path
    ioTDBConfig.dataDir = file.getPath();
    ioTDBConfig.updateConfigForPath();
    ioTDBConfig.walFolder = options.getWalPath();
    // update options
    ioTDBConfig.updateOptions(options);
    // 恢复MManager
    mManager = MManager.getInstance();
    // 恢复FileNodeManager
    fileNodeManager = FileNodeManager.getInstance();
    fileNodeManager.recovery();
    // 恢复WAL模块
    dataRecovery();
    // 恢复close and merge service
    try {
      CloseMergeService.getInstance().start();
    } catch (StartupException e) {
      throw new IoTDBEngineException(e);
    }
    // 恢复FileReaderManager
    try {
      FileReaderManager.getInstance().start();
    } catch (StartupException e) {
      e.printStackTrace();
      throw new IoTDBEngineException(e);
    }
    // 启动WAL模块
    try {
      MultiFileLogNodeManager.getInstance().start();
    } catch (StartupException e) {
      e.printStackTrace();
      throw new IoTDBEngineException(e);
    }
    // 构造overflow query process executor
    overflowQPExecutor = new OverflowQPExecutor();
    queryProcessor = new QueryProcessor(overflowQPExecutor);
  }

  private void dataRecovery() throws IoTDBEngineException {
    LOGGER.info("{}: start checking write log...", IoTDBConstant.GLOBAL_DB_NAME);
    // QueryProcessor processor = new QueryProcessor(new OverflowQPExecutor());
    WriteLogNodeManager writeLogManager = MultiFileLogNodeManager.getInstance();
    List<String> filenodeNames = null;
    try {
      filenodeNames = MManager.getInstance().getAllFileNames();
    } catch (PathErrorException e) {
      throw new IoTDBEngineException(e);
    }
    for (String filenodeName : filenodeNames) {
      if (writeLogManager.hasWAL(filenodeName)) {
        try {
          FileNodeManager.getInstance().recoverFileNode(filenodeName);
        } catch (FileNodeProcessorException | FileNodeManagerException e) {
          throw new IoTDBEngineException(e);
        }
      }
    }
    IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();
    boolean enableWal = config.enableWal;
    config.enableWal = false;
    try {
      writeLogManager.recover();
    } catch (RecoverException e) {
      e.printStackTrace();
      throw new IoTDBEngineException(e);
    }
    config.enableWal = enableWal;
  }

  @Override
  public void close() throws IOException {
    // close all filenode manger
    try {
      FileNodeManager.getInstance().closeAll();
    } catch (FileNodeManagerException e) {
      e.printStackTrace();
    }
    // close metadata
    MManager.getInstance().flushObjectToFile();
    // close merge and close service
    CloseMergeService.getInstance().stop();
    // close reader manager
    FileReaderManager.getInstance().stop();
    FileReaderManager.getInstance().closeAndRemoveAllOpenedReaders();
    MultiFileLogNodeManager.getInstance().stop();
    this.lock.release();
  }

  @Override
  public void write(String deviceId, long insertTime, List<String> measurementList,
      List<String> insertValues) throws IOException {
    try {
      overflowQPExecutor.multiInsert(deviceId, insertTime, measurementList, insertValues);
    } catch (ProcessorException e) {
      throw new IOException(e);
    }
  }

  @Override
  public synchronized QueryDataSet query(String timeseries, long startTime, long endTime)
      throws IOException {
    try {
      return queryProcessor.getExecutor().timeRangeQuery(timeseries, startTime, endTime);
    } catch (FileNodeManagerException e) {
      e.printStackTrace();
      throw new IOException(e);
    }
  }
  public void endQuery(){
    try {
      QueryTokenManager.getInstance().endQueryForCurrentRequestThread();
    } catch (FileNodeManagerException e) {
      e.printStackTrace();
    }
  }

  @Override
  public IoTDBOptions getOptions() {
    return options;
  }

  @Override
  public void setStorageGroup(String storageGroup) throws IOException {
    try {
      mManager.setStorageLevelToMTree(storageGroup);
    } catch (PathErrorException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void addTimeSeries(String path, String dataType, String encoding, String[] args)
      throws IOException {
    try {
      mManager.addPathToMTree(path, dataType, encoding, args);
    } catch (PathErrorException e) {
      e.printStackTrace();
      throw new IOException(e);
    } catch (MetadataArgsErrorException e) {
      e.printStackTrace();
      throw new IOException(e);
    }
  }
}
