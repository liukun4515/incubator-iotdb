package org.apache.iotdb.db.api;

import java.io.IOException;
import java.util.List;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

/**
 * Created by liukun on 19/3/7.
 */
public interface ITSEngine {

  /**
   * Open or Create the Engine if not exist.
   * @throws IoTDBEngineException
   */
  void openOrCreate() throws IoTDBEngineException;

  /**
   * close the iotdb engine, which can only be call once.
   */
  void close() throws IOException;

  void write(String deviceId, long insertTime, List<String> measurementList,
      List<String> insertValues) throws IOException;

  QueryDataSet query(String timeseries, long startTime, long endTime) throws IOException;

  IoTDBOptions getOptions();

  void setStorageGroup(String storageGroup) throws IOException;

  void addTimeSeries(String path, String dataType, String encoding, String[] args)
      throws IOException;
}
