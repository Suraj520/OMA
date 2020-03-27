package it.unibo.cvlab.datasetgenerator.save;

import android.hardware.SensorEvent;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class SensorData {

    public static SensorData getFromSensorEvent(@NonNull SensorEvent event){
        return new SensorData(event.sensor.getName(), event.sensor.getId(), event.timestamp, event.accuracy, event.values);
    }

    @SerializedName("sensorName")
    private String sensorName;
    @SerializedName("id")
    private int id;

    @SerializedName("timestamp")
    private long timestamp;
    @SerializedName("accuracy")
    private int accuracy;
    @SerializedName("data")
    private float[] data;

    private SensorData(String sensorName, int id, long timestamp, int accuracy, float[] data) {
        this.sensorName = sensorName;
        this.id = id;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.data = data;
    }

    public String getSensorName() {
        return sensorName;
    }

    public int getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public float[] getData() {
        return data;
    }
}
