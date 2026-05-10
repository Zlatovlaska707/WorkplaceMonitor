package com.example.accelerometer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "MySharedPref";
    private static final String KEY_TOPIC = "Topic";
    private static final String KEY_SERVER_URI = "serverURI";
    private static final String KEY_USERNAME = "Username";
    private static final String KEY_PASSWORD = "Password";
    private static final String KEY_ACCEL_ENABLED = "SensorAccelerometerEnabled";
    private static final String KEY_LIGHT_ENABLED = "SensorLightEnabled";
    private static final String KEY_PROXIMITY_ENABLED = "SensorProximityEnabled";
    private static final String KEY_STEPS_ENABLED = "SensorStepCounterEnabled";
    private static final String KEY_MIC_ENABLED = "SensorMicrophoneEnabled";
    private static final String KEY_PUBLISH_INTERVAL_MS = "PublishIntervalMs";
    private static final int REQUEST_PERMISSIONS_CODE = 1201;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor lightSensor;
    private Sensor proximitySensor;
    private Sensor stepCounterSensor;

    private MqttAndroidClient client;
    private MediaRecorder mediaRecorder;
    private boolean isMicRecorderStarted = false;

    private TextView xVal, yVal, zVal;
    private Button settings, btnStart, btnStop, btnAboutUs;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    Runnable mRunnableTask;

    private boolean isPublishing = false;
    private boolean accelerometerEnabled = true;
    private boolean lightEnabled = true;
    private boolean proximityEnabled = true;
    private boolean stepCounterEnabled = true;
    private boolean microphoneEnabled = true;
    private long publishIntervalMs = 5000L;

    private float latestX = 0f;
    private float latestY = 0f;
    private float latestZ = 0f;
    private float latestLight = 0f;
    private float latestProximity = 0f;
    private long latestStepsDelta = 0L;
    private float baseStepCounterValue = -1f;
    private int latestMicPeak = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Available sensors (may be null on some devices)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        // Text View
        xVal = findViewById(R.id.xValue);
        yVal = findViewById(R.id.yVlaue);
        zVal = findViewById(R.id.zValue);

        // Buttons
        settings = findViewById(R.id.btnSettings);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnAboutUs = findViewById(R.id.btnAboutUs);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                latestX = event.values[0];
                latestY = event.values[1];
                latestZ = event.values[2];
                xVal.setText("X: " + latestX);
                yVal.setText("Y: " + latestY);
                zVal.setText("Z: " + latestZ);
                break;
            case Sensor.TYPE_LIGHT:
                latestLight = event.values[0];
                break;
            case Sensor.TYPE_PROXIMITY:
                latestProximity = event.values[0];
                break;
            case Sensor.TYPE_STEP_COUNTER:
                float currentTotalSteps = event.values[0];
                if (baseStepCounterValue < 0f) {
                    baseStepCounterValue = currentTotalSteps;
                }
                latestStepsDelta = Math.max(0L, (long) (currentTotalSteps - baseStepCounterValue));
                break;
            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not in use
    }

    /**
     * Загружает сертификат из папки assets и создает SSLSocketFactory
     */
    private SSLSocketFactory getSocketFactoryFromAssets() {
        try {
            // Загружаем сертификат из assets
            InputStream caInput = getAssets().open("rootca.crt");

            // Создаем фабрику сертификатов
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca = cf.generateCertificate(caInput);

            // Создаем KeyStore с сертификатом
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Создаем TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Создаем SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            caInput.close();

            Log.d("MainActivity", "SSL Socket Factory successfully created from certificate");
            return sslContext.getSocketFactory();

        } catch (Exception e) {
            Log.e("MainActivity", "Error loading certificate: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Временный метод для отладки (доверяет всем сертификатам)
     * ИСПОЛЬЗОВАТЬ ТОЛЬКО ДЛЯ ТЕСТИРОВАНИЯ!
     */
    private SSLSocketFactory getUnsafeSocketFactory() {
        try {
            // Создаем TrustManager, который доверяет всем сертификатам
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            Log.d("MainActivity", "Unsafe SSL Socket Factory created (WARNING: not secure)");
            return sslContext.getSocketFactory();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void startPublish(View v) {
        if (isPublishing) {
            Toast.makeText(MainActivity.this, "Publishing is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        loadSettings();
        applyPermissionRestrictions();

        registerConfiguredSensors();

        if (microphoneEnabled) {
            startMicrophoneRecorder();
        }

        SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String serverURI = sharedPref.getString(KEY_SERVER_URI, "");
        String userId = sharedPref.getString(KEY_USERNAME, "");
        String userPassword = sharedPref.getString(KEY_PASSWORD, "");

        if (serverURI.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please configure MQTT settings first", Toast.LENGTH_LONG).show();
            stopMicrophoneRecorder();
            return;
        }

        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), serverURI, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(userId);
        options.setPassword(userPassword.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        // Устанавливаем SocketFactory с нашим сертификатом
        SSLSocketFactory socketFactory = getSocketFactoryFromAssets();

        if (socketFactory != null) {
            options.setSocketFactory(socketFactory);
            Log.d("MainActivity", "Using secure socket factory with certificate");
        } else {
            Log.w("MainActivity", "Certificate loading failed, connection may fail");
            // Если хотите использовать небезопасное соединение для отладки, раскомментируйте:
            // options.setSocketFactory(getUnsafeSocketFactory());
        }

        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Toast.makeText(MainActivity.this, "Connected to MQTT broker", Toast.LENGTH_LONG).show();
                    Log.d("MainActivity", "Successfully connected to " + serverURI);
                    isPublishing = true;

                    // Публикуем первое сообщение сразу после подключения
                    publish();

                    // Запускаем периодическую публикацию
                    mRunnableTask = new Runnable() {
                        @Override
                        public void run() {
                            publish();
                            mHandler.postDelayed(this, publishIntervalMs);
                        }
                    };
                    mHandler.postDelayed(mRunnableTask, publishIntervalMs);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMsg = "Connection failed: " + exception.getMessage();
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    Log.e("MainActivity", errorMsg);
                    exception.printStackTrace();
                    isPublishing = false;
                    stopMicrophoneRecorder();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopMicrophoneRecorder();
        }
    }

    public void publish() {
        if (client == null || !client.isConnected()) {
            Log.e("MainActivity", "Cannot publish: client not connected");
            Toast.makeText(MainActivity.this, "Not connected to broker", Toast.LENGTH_SHORT).show();
            return;
        }

        if (microphoneEnabled) {
            latestMicPeak = readMicPeakValue();
        } else {
            latestMicPeak = 0;
        }

        SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String mqttTopic = sharedPref.getString(KEY_TOPIC, "");

        if (mqttTopic.isEmpty()) {
            Log.e("MainActivity", "Topic is empty");
            Toast.makeText(MainActivity.this, "Topic is not configured", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("MainActivity", "Publishing to topic: " + mqttTopic);

        float sendX = (accelerometerEnabled && accelerometer != null) ? latestX : 0f;
        float sendY = (accelerometerEnabled && accelerometer != null) ? latestY : 0f;
        float sendZ = (accelerometerEnabled && accelerometer != null) ? latestZ : 0f;
        float sendLight = (lightEnabled && lightSensor != null) ? latestLight : 0f;
        float sendProximity = (proximityEnabled && proximitySensor != null) ? latestProximity : 0f;
        long sendSteps = (stepCounterEnabled && stepCounterSensor != null) ? latestStepsDelta : 0L;
        int sendMic = microphoneEnabled ? latestMicPeak : 0;

        String message = "X:" + sendX + "," +
                "Y:" + sendY + "," +
                "Z:" + sendZ + "," +
                "L:" + sendLight + "," +
                "P:" + sendProximity + "," +
                "S:" + sendSteps + "," +
                "M:" + sendMic;

        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(0); // QoS 0 - at most once
            client.publish(mqttTopic, mqttMessage);
            Log.d("MainActivity", "Published: " + message);
        } catch (MqttException e) {
            Log.e("MainActivity", "Publish failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopPublish(View v) {
        stopPublishingInternal();
    }

    private void stopPublishingInternal() {
        isPublishing = false;
        if (mRunnableTask != null) {
            mHandler.removeCallbacks(mRunnableTask);
            Log.d("MainActivity", "Stopped periodic publishing");
        }

        stopMicrophoneRecorder();

        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                Log.d("MainActivity", "Disconnected from broker");
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        client = null;
    }

    public void handleClick(View v) {
        startActivity(new Intent(MainActivity.this, MqttSettings.class));
    }

    public void handleAboutUs(View v) {
        startActivity(new Intent(MainActivity.this, AboutUs.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        registerConfiguredSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterAllSensors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPublishingInternal();
        unregisterAllSensors();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void loadSettings() {
        SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accelerometerEnabled = sharedPref.getBoolean(KEY_ACCEL_ENABLED, true);
        lightEnabled = sharedPref.getBoolean(KEY_LIGHT_ENABLED, true);
        proximityEnabled = sharedPref.getBoolean(KEY_PROXIMITY_ENABLED, true);
        stepCounterEnabled = sharedPref.getBoolean(KEY_STEPS_ENABLED, true);
        microphoneEnabled = sharedPref.getBoolean(KEY_MIC_ENABLED, true);
        publishIntervalMs = readPublishIntervalMs(sharedPref);
        if (publishIntervalMs < 1000L) {
            publishIntervalMs = 1000L;
        }
    }

    private long readPublishIntervalMs(SharedPreferences sharedPref) {
        long defaultValue = 5000L;
        try {
            return sharedPref.getLong(KEY_PUBLISH_INTERVAL_MS, defaultValue);
        } catch (ClassCastException ignored) {
            // Backward compatibility: older app versions could save this key as int/string.
            try {
                int legacyValue = sharedPref.getInt(KEY_PUBLISH_INTERVAL_MS, (int) defaultValue);
                sharedPref.edit().putLong(KEY_PUBLISH_INTERVAL_MS, legacyValue).apply();
                return legacyValue;
            } catch (ClassCastException ignoredAgain) {
                String legacyString = sharedPref.getString(KEY_PUBLISH_INTERVAL_MS, String.valueOf(defaultValue));
                try {
                    long parsed = Long.parseLong(legacyString);
                    sharedPref.edit().putLong(KEY_PUBLISH_INTERVAL_MS, parsed).apply();
                    return parsed;
                } catch (Exception e) {
                    return defaultValue;
                }
            }
        }
    }

    private void registerConfiguredSensors() {
        unregisterAllSensors();

        if (accelerometerEnabled && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (lightEnabled && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (proximityEnabled && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (stepCounterEnabled && stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterAllSensors() {
        sensorManager.unregisterListener(this);
    }

    private void applyPermissionRestrictions() {
        java.util.ArrayList<String> missingPermissions = new java.util.ArrayList<>();

        boolean hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (microphoneEnabled && !hasMicPermission) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
            microphoneEnabled = false;
            latestMicPeak = 0;
        }

        boolean hasActivityRecognitionPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
        if (stepCounterEnabled && !hasActivityRecognitionPermission) {
            missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
            stepCounterEnabled = false;
            latestStepsDelta = 0;
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS_CODE
            );
            Toast.makeText(
                    this,
                    "Optional permissions missing: MQTT publishing will continue without some sensors",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void startMicrophoneRecorder() {
        if (!microphoneEnabled || isMicRecorderStarted) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile("/dev/null");
            mediaRecorder.prepare();
            mediaRecorder.start();
            isMicRecorderStarted = true;
        } catch (Exception e) {
            Log.e("MainActivity", "Microphone start failed: " + e.getMessage());
            stopMicrophoneRecorder();
        }
    }

    private int readMicPeakValue() {
        if (!isMicRecorderStarted || mediaRecorder == null) {
            return 0;
        }
        try {
            return mediaRecorder.getMaxAmplitude();
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to read mic peak: " + e.getMessage());
            return 0;
        }
    }

    private void stopMicrophoneRecorder() {
        if (mediaRecorder != null) {
            try {
                if (isMicRecorderStarted) {
                    mediaRecorder.stop();
                }
            } catch (Exception e) {
                Log.w("MainActivity", "Microphone stop failed: " + e.getMessage());
            }

            try {
                mediaRecorder.reset();
            } catch (Exception ignored) {
            }

            mediaRecorder.release();
            mediaRecorder = null;
        }
        isMicRecorderStarted = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS_CODE) {
            return;
        }

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            Toast.makeText(this, "Permissions granted. Press Start again.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Required permissions were not granted.", Toast.LENGTH_LONG).show();
        }
    }
}