package com.example.accelerometer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private MqttAndroidClient client;

    private TextView xVal, yVal, zVal;
    private Button settings, btnStart, btnStop, btnAboutUs;

    Handler mHandler = new Handler();
    Runnable mRunnableTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //Accelerometer Sensor
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //Register Sensor Listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        //Text View
        xVal = findViewById(R.id.xValue);
        yVal = findViewById(R.id.yVlaue);
        zVal = findViewById(R.id.zValue);

        //Buttons
        settings = findViewById(R.id.btnSettings);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnAboutUs = findViewById(R.id.btnAboutUs);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        xVal.setText("X: " + event.values[0]);
        yVal.setText("Y: " + event.values[1]);
        zVal.setText("Z: " + event.values[2]);
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
        SharedPreferences sharedPref = this.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        String serverURI = sharedPref.getString("serverURI", "");

        String userId = sharedPref.getString("Username", "");
        String userPassword = sharedPref.getString("Password", "");

        if (serverURI.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please configure MQTT settings first", Toast.LENGTH_LONG).show();
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

                    // Публикуем первое сообщение сразу после подключения
                    publish();

                    // Запускаем периодическую публикацию каждые 5 секунд
                    mRunnableTask = new Runnable() {
                        @Override
                        public void run() {
                            publish();
                            mHandler.postDelayed(this, 5000);
                        }
                    };
                    mHandler.postDelayed(mRunnableTask, 5000);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMsg = "Connection failed: " + exception.getMessage();
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    Log.e("MainActivity", errorMsg);
                    exception.printStackTrace();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void publish() {
        if (client == null || !client.isConnected()) {
            Log.e("MainActivity", "Cannot publish: client not connected");
            Toast.makeText(MainActivity.this, "Not connected to broker", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPref = this.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        String mqttTopic = sharedPref.getString("Topic", "");

        if (mqttTopic.isEmpty()) {
            Log.e("MainActivity", "Topic is empty");
            Toast.makeText(MainActivity.this, "Topic is not configured", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("MainActivity", "Publishing to topic: " + mqttTopic);

        String message = xVal.getText().toString() + "," +
                yVal.getText().toString() + "," +
                zVal.getText().toString();

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
        if (mRunnableTask != null) {
            mHandler.removeCallbacks(mRunnableTask);
            Log.d("MainActivity", "Stopped periodic publishing");
        }

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
    }

    public void handleClick(View v) {
        startActivity(new Intent(MainActivity.this, MqttSettings.class));
    }

    public void handleAboutUs(View v) {
        startActivity(new Intent(MainActivity.this, AboutUs.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Очищаем ресурсы при уничтожении активности
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        mHandler.removeCallbacksAndMessages(null);
    }
}