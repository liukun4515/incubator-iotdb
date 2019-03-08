package org.apache.iotdb.db.api.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.api.ITSEngine;
import org.apache.iotdb.db.api.IoTDBEngineException;
import org.apache.iotdb.db.api.IoTDBOptions;
import org.apache.iotdb.db.api.impl.IoTDBEngine;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

/**
 * Created by liukun on 19/3/8.
 */
public class OneThreadWrite {

  public static void main(String[] args) throws IOException, IoTDBEngineException {
    File file = new File("testdb");
    IoTDBOptions options = new IoTDBOptions();
    ITSEngine db = new IoTDBEngine(file, options);
    // open the database
    db.openOrCreate();
    // create schema
    db.setStorageGroup("root.beijing");
    db.addTimeSeries("root.beijing.d0.s0", TSDataType.INT32.toString(),
        TSEncoding.GORILLA.toString(), new String[0]);
    db.addTimeSeries("root.beijing.d0.s1", TSDataType.INT32.toString(),
        TSEncoding.GORILLA.toString(), new String[0]);
    db.addTimeSeries("root.beijing.d0.s2", TSDataType.INT32.toString(),
        TSEncoding.GORILLA.toString(), new String[0]);
    db.addTimeSeries("root.beijing.d0.s3", TSDataType.INT32.toString(),
        TSEncoding.GORILLA.toString(), new String[0]);
    // insert data
    List<String> measurementIDs = new ArrayList<>();
    measurementIDs.add("s0");
    measurementIDs.add("s1");
    measurementIDs.add("s2");
    measurementIDs.add("s3");
    List<String> values = new ArrayList<>();
    values.add(Integer.toString(1));
    values.add(Integer.toString(2));
    values.add(Integer.toString(3));
    values.add(Integer.toString(4));
    String deviceid = "root.beijing.d0";
    for (int i = 0; i < 1000; i++) {
      db.write(deviceid, i, measurementIDs, values);
    }
    QueryDataSet dataSet = db.query("root.beijing.d0.s0",100,200);
    int count = 0;
    while(dataSet.hasNext()){
      count++;
      dataSet.next();
    }
    System.out.println(count);
    db.close();
  }
}
