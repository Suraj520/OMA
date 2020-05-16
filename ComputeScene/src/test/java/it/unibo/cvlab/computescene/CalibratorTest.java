package it.unibo.cvlab.computescene;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class CalibratorTest {

    private Calibrator.MinimumSquareObject[] minimumSquareObjects;
    private Calibrator.RansacObject[] ransacObjects;

    private double scaleResult;
    private double shiftResult;

    private Calibrator calibrator;

    private void load8bit() throws Exception {
        ArrayList<Calibrator.MinimumSquareObject> minimumSquareObjectArrayList = new ArrayList<>();
        ArrayList<Calibrator.RansacObject> ransacObjectArrayList = new ArrayList<>();

        InputStream gtInputStream = Files.newInputStream(Paths.get("test/gt_8bit.raw"));
        InputStream predictionInputStream = Files.newInputStream(Paths.get("test/prediction_8bit.raw"));

        //8 bit depth * W * H
        byte[] gtByteArray = new byte[1* 640 * 480];
        byte[] predictionByteArray = new byte[1 * 640 * 480];

        gtInputStream.read(gtByteArray);
        predictionInputStream.read(predictionByteArray);

        gtInputStream.close();
        predictionInputStream.close();


        for(int i = 0; i < gtByteArray.length; i++){
            int gtNextValue = gtByteArray[i] & 0xFF;
            int predictionNextValue = predictionByteArray[i] & 0xFF;

            if(gtNextValue != 0){
                double distance = gtNextValue / 1.0;
                double predictedDistance = predictionNextValue / 1.0;

                Calibrator.MinimumSquareObject minimumSquareObject = new Calibrator.MinimumSquareObject();
                minimumSquareObject.distance = distance;
                minimumSquareObject.predictedDistance = predictedDistance;
                minimumSquareObject.arConfidence = 1.0;
                minimumSquareObject.calculate();

                minimumSquareObjectArrayList.add(minimumSquareObject);


                Calibrator.RansacObject ransacObject = new Calibrator.RansacObject();
                ransacObject.distance = distance;
                ransacObject.predictedDistance = predictedDistance;
                ransacObject.arConfidence = 1.0;

                ransacObjectArrayList.add(ransacObject);
            }
        }

        minimumSquareObjects = minimumSquareObjectArrayList.toArray(new Calibrator.MinimumSquareObject[0]);
        ransacObjects = ransacObjectArrayList.toArray(new Calibrator.RansacObject[0]);
    }

    private void load16bit() throws Exception {
        ArrayList<Calibrator.MinimumSquareObject> minimumSquareObjectArrayList = new ArrayList<>();
        ArrayList<Calibrator.RansacObject> ransacObjectArrayList = new ArrayList<>();

        InputStream gtInputStream = Files.newInputStream(Paths.get("test/gt_16bit.raw"));
        InputStream predictionInputStream = Files.newInputStream(Paths.get("test/prediction_16bit.raw"));

        //8 bit depth * W * H
        byte[] gtByteArray = new byte[2* 640 * 480];
        byte[] predictionByteArray = new byte[2 * 640 * 480];

        gtInputStream.read(gtByteArray);
        predictionInputStream.read(predictionByteArray);

        gtInputStream.close();
        predictionInputStream.close();

        ByteBuffer gtByteBuffer = ByteBuffer.wrap(gtByteArray);
        gtByteBuffer.order(ByteOrder.BIG_ENDIAN);

        ByteBuffer predictionByteBuffer = ByteBuffer.wrap(predictionByteArray);
        predictionByteBuffer.order(ByteOrder.BIG_ENDIAN);

        ShortBuffer gtBuffer = gtByteBuffer.asShortBuffer();
        ShortBuffer predictionBuffer = predictionByteBuffer.asShortBuffer();

        for(int i = 0; i < gtByteArray.length; i+=2){

            int gtNextValue = gtBuffer.get() & 0xFFFF;
            int predictionNextValue = predictionBuffer.get() & 0xFFFF;

            //Applico la maschera
            if(gtNextValue != 0){
                double distance = gtNextValue / 256.0;
                double predictedDistance = predictionNextValue / 256.0;

                Calibrator.MinimumSquareObject minimumSquareObject = new Calibrator.MinimumSquareObject();
                minimumSquareObject.distance = distance;
                minimumSquareObject.predictedDistance = predictedDistance;
                minimumSquareObject.arConfidence = 1.0;
                minimumSquareObject.calculate();

                minimumSquareObjectArrayList.add(minimumSquareObject);

                Calibrator.RansacObject ransacObject = new Calibrator.RansacObject();
                ransacObject.distance = distance;
                ransacObject.predictedDistance = predictedDistance;
                ransacObject.arConfidence = 1.0;

                ransacObjectArrayList.add(ransacObject);
            }
        }

        minimumSquareObjects = minimumSquareObjectArrayList.toArray(new Calibrator.MinimumSquareObject[0]);
        ransacObjects = ransacObjectArrayList.toArray(new Calibrator.RansacObject[0]);
    }

    @Before
    public void setUp() throws Exception {

        calibrator = new Calibrator();

        load16bit();

        scaleResult = 0.0011021982754020616;
        shiftResult = 0.16692999005317688;
    }

    @Test
    public void calibrateScaleFactorQuadratiMinimi() {

        calibrator.calibrateScaleFactorQuadratiMinimi(minimumSquareObjects);

        System.out.println("Scale:"+calibrator.getScaleFactor());
        System.out.println("Shift:"+calibrator.getShiftFactor());

        assertEquals(scaleResult, calibrator.getScaleFactor(), 0.0001);
        assertEquals(shiftResult, calibrator.getShiftFactor(), 0.0001);
    }
}