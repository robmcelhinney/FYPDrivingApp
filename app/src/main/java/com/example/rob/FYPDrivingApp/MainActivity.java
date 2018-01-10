package com.example.rob.FYPDrivingApp;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;

public class MainActivity extends AppCompatActivity implements SensorEventListener,
        TextToSpeech.OnInitListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status>
{

    public GoogleApiClient mApiClient;

    private NotificationManager mNotificationManager;

    private MyBroadcastReceiver myBroadcastReceiver;

    private static final int N_SAMPLES = 200;
    private static List<Float> x;
    private static List<Float> y;
    private static List<Float> z;
    private TextToSpeech textToSpeech;
    private float[] results;
    private TensorFlowClassifier classifier;

    private TextView greatestProb;

    private float sittingcarValue = 0;

    private float greatestProbValue = 0;

    private TextView sittingcarTextView;

    private TextView currText;

    private TextView BTtextView;

    private boolean sittingIntoCar = false;

    private boolean onFoot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        x = new ArrayList<>();
        y = new ArrayList<>();
        z = new ArrayList<>();

        greatestProb = (TextView) findViewById(R.id.greatestProb);

        sittingcarTextView = (TextView) findViewById(R.id.sittingcar_prob);

        currText = (TextView) findViewById(R.id.currText);

        BTtextView = (TextView) findViewById(R.id.BTtextView);

        classifier = new TensorFlowClassifier(getApplicationContext());

        textToSpeech = new TextToSpeech(this, this);
        textToSpeech.setLanguage(Locale.US);

        myBroadcastReceiver = new MyBroadcastReceiver();

        //register BroadcastReceiver
        IntentFilter intentFilter = new IntentFilter(ActivityRecognizedService.ACTION_ActivityRecognizedService);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(myBroadcastReceiver, intentFilter);


        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!mNotificationManager.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        }

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();
    }

    @Override
    public void onInit(int status) {
        Timer timer = new Timer();
//        timer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                if (results == null || results.length == 0) {
//                    return;
//                }
//                float max = -1;
//                int idx = -1;
//                for (int i = 0; i < results.length; i++) {
//                    if (results[i] > max) {
//                        idx = i;
//                        max = results[i];
//                    }
//                }
//                if(sittingcarValue > 0.15) {
//                    String sittingCarString = "Sitting into Car is" + sittingcarValue;
//                    textToSpeech.speak(sittingCarString, TextToSpeech.QUEUE_ADD, null, Integer.toString(new Random().nextInt()));
//                }
//            }
//        }, 2000, 5000);
    }

    protected void onPause() {
        getSensorManager().unregisterListener(this);
        super.onPause();
    }

    protected void onResume() {
        super.onResume();
        textToSpeech.speak("Gucci Gang Gucci Gang.", TextToSpeech.QUEUE_ADD, null, Integer.toString(new Random().nextInt()));
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        activityPrediction();
        x.add(event.values[0]);
        y.add(event.values[1]);
        z.add(event.values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void activityPrediction() {
        if (x.size() == N_SAMPLES && y.size() == N_SAMPLES && z.size() == N_SAMPLES && (x.size() % 20 == 0 && y.size() % 20 == 0 && z.size() % 20 == 0)) {
            List<Float> data = new ArrayList<>();
            data.addAll(x);
            data.addAll(y);
            data.addAll(z);

            results = classifier.predictProbabilities(toFloatArray(data));

            sittingcarValue = round(results[2], 2);
            sittingcarTextView.setText(Float.toString(sittingcarValue));

            if(greatestProbValue < sittingcarValue) {
                greatestProbValue = sittingcarValue;
                greatestProb.setText(String.valueOf(greatestProbValue));

                if(greatestProbValue > 0.8){
                    String sittingCarString = "Sitting into car is" + greatestProbValue;
                    textToSpeech.speak(sittingCarString, TextToSpeech.QUEUE_ADD, null, Integer.toString(new Random().nextInt()));

                    sittingIntoCar = true;
                }
            }

            List<Float> subListx = (List<Float>) x.subList(0, 19);
            subListx.clear();
            List<Float> subListy = (List<Float>) y.subList(0, 19);
            subListy.clear();
            List<Float> subListz = (List<Float>) z.subList(0, 19);
            subListz.clear();
        }
    }

    private float[] toFloatArray(List<Float> list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e( "ActivityRecogition", "hello" );

        Intent intent = new Intent( this, ActivityRecognizedService.class );
        PendingIntent pendingIntent = PendingIntent.getService( this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mApiClient, 3000, pendingIntent );
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResult(Status status) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //un-register BroadcastReceiver
        unregisterReceiver(myBroadcastReceiver);
    }

    // Receiving data back from ActivityRecognizedService
    // Receives the activity and confidence
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String activity = intent.getStringExtra(ActivityRecognizedService.EXTRA_KEY_OUT_ACTIVITY);
            String confidence = intent.getStringExtra(ActivityRecognizedService.EXTRA_KEY_OUT_CONFIDENCE);
            float conf = Float.parseFloat(confidence);
            if (activity.equalsIgnoreCase("ON_FOOT") && conf > 0.9){
                try {
                    makeNoise();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (sittingIntoCar){sittingIntoCar = false;}

                if (!onFoot) {
                    onResume();
                }

                onFoot = true;
            }
            else {
                if(activity.equalsIgnoreCase("IN_VEHICLE") && conf > 0.9 && sittingIntoCar && mNotificationManager.isNotificationPolicyAccessGranted()) {
                    if (mNotificationManager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_NONE) {
//                        mNotificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);



                        mNotificationManager.setInterruptionFilter(NotificationManager.IMPORTANCE_MIN);



                        textToSpeech.speak("Turning on Do not Disturb as I think you're driving a car.", TextToSpeech.QUEUE_ADD, null, Integer.toString(new Random().nextInt()));
                    }
                }
                onPause();

                onFoot = false;
            }
            currText.setText(activity);




            //Testing Bluetooth
            if (activity.equalsIgnoreCase("STILL")){
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()
                        && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED) {
//                    getDeviceClass();

                    java.util.Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    for (BluetoothDevice device : pairedDevices) {
                        BluetoothClass bluetoothClass = device.getBluetoothClass();
                        if (bluetoothClass != null) {
                            int deviceClass = bluetoothClass.getDeviceClass();
//                            if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
//                            BTtextView.setText(bluetoothClass.getMajorDeviceClass());

                            BTtextView.setText(deviceClass + " : " + BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO);
//                            Toast.makeText(MainActivity.this, bluetoothClass.getMajorDeviceClass(),
//                                    Toast.LENGTH_LONG).show();
//                            }
                        }
                    }
                }
            }
        }
    }


    public void makeNoise() {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        r.play();
    }
}