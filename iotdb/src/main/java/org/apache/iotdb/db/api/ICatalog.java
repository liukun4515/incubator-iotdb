package org.apache.iotdb.db.api;


/**
 * Created by liukun on 19/3/7.
 */
public interface ICatalog {

    void setStorage() throws IoTDBEngineException;

    void addTimeSeries() throws IoTDBEngineException;

}
