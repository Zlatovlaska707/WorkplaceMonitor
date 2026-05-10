package com.example.accelerometer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

public class MqttSettings extends AppCompatActivity {

    private static final String PREFS_NAME = "MySharedPref";
    private static final String KEY_TOPIC = "Topic";
    private static final String KEY_SERVER_URI = "serverURI";
    private static final String KEY_BROKER = "Broker";
    private static final String KEY_PORT = "Port";
    private static final String KEY_USERNAME = "Username";
    private static final String KEY_PASSWORD = "Password";
    private static final String KEY_ACCEL_ENABLED = "SensorAccelerometerEnabled";
    private static final String KEY_LIGHT_ENABLED = "SensorLightEnabled";
    private static final String KEY_STEPS_ENABLED = "SensorStepCounterEnabled";
    private static final String KEY_MIC_ENABLED = "SensorMicrophoneEnabled";
    private static final String KEY_PUBLISH_INTERVAL_MS = "PublishIntervalMs";

    private EditText broker, port, topic, username, password;
    private EditText publishIntervalMs;
    private SwitchCompat accelSwitch, lightSwitch, stepCounterSwitch, micSwitch;
    private Button connect;
    private String serverURI;
    private String mqttTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_settings);

        broker = findViewById(R.id.txtBroker);
        port = findViewById(R.id.txtPort);
        topic = findViewById(R.id.txtTopic);
        username = findViewById(R.id.txtUsername);
        password = findViewById(R.id.pwPassword);
        publishIntervalMs = findViewById(R.id.txtPublishIntervalMs);
        accelSwitch = findViewById(R.id.switchAccelerometer);
        lightSwitch = findViewById(R.id.switchLight);
        stepCounterSwitch = findViewById(R.id.switchStepCounter);
        micSwitch = findViewById(R.id.switchMicrophone);
        connect = findViewById(R.id.btnConnect);

        // Загружаем ВСЕ сохраненные настройки
        loadSavedSettings();
    }

    /**
     * Загружает все сохраненные настройки из SharedPreferences
     */
    private void loadSavedSettings() {
        SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Загружаем Broker и Port (их не было в загрузке!)
        String mqttBroker = sharedPref.getString(KEY_BROKER, "");
        String mqttPort = sharedPref.getString(KEY_PORT, "");
        String mqttTopic = sharedPref.getString(KEY_TOPIC, "");
        String mqttUsername = sharedPref.getString(KEY_USERNAME, "");
        String mqttPassword = sharedPref.getString(KEY_PASSWORD, "");
        long savedIntervalMs = readPublishIntervalMs(sharedPref);

        // Устанавливаем значения в поля ввода
        broker.setText(mqttBroker);
        port.setText(mqttPort);
        topic.setText(mqttTopic);
        username.setText(mqttUsername);
        password.setText(mqttPassword);
        publishIntervalMs.setText(String.valueOf(savedIntervalMs));

        accelSwitch.setChecked(sharedPref.getBoolean(KEY_ACCEL_ENABLED, true));
        lightSwitch.setChecked(sharedPref.getBoolean(KEY_LIGHT_ENABLED, true));
        stepCounterSwitch.setChecked(sharedPref.getBoolean(KEY_STEPS_ENABLED, true));
        micSwitch.setChecked(sharedPref.getBoolean(KEY_MIC_ENABLED, true));

        Log.d("MqttSettings", "Loaded settings - Broker: " + mqttBroker + ", Port: " + mqttPort);
    }

    private long readPublishIntervalMs(SharedPreferences sharedPref) {
        long defaultValue = 5000L;
        try {
            return sharedPref.getLong(KEY_PUBLISH_INTERVAL_MS, defaultValue);
        } catch (ClassCastException ignored) {
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

    public void connectMqtt(View v) {
        // Проверяем, что поля не пустые
        if (broker.getText().toString().isEmpty() || port.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please enter Broker and Port", Toast.LENGTH_SHORT).show();
            return;
        }

        // Формируем serverURI (убеждаемся, что нет двойного двоеточия)
        String brokerText = broker.getText().toString();
        String portText = port.getText().toString();

        // Если broker уже содержит "://", не добавляем лишнее
        if (brokerText.contains("://")) {
            serverURI = brokerText + ":" + portText;
        } else {
            serverURI = brokerText + ":" + portText;
        }

        Log.d("MqttSettings", "Server URI: " + serverURI);

        long parsedIntervalMs;
        try {
            parsedIntervalMs = Long.parseLong(publishIntervalMs.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "Publish interval must be a number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (parsedIntervalMs < 1000L) {
            Toast.makeText(this, "Publish interval must be >= 1000 ms", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        mqttTopic = topic.getText().toString();

        // Сохраняем ВСЕ поля
        editor.putString(KEY_TOPIC, mqttTopic);
        editor.putString(KEY_SERVER_URI, serverURI);
        editor.putString(KEY_BROKER, broker.getText().toString());
        editor.putString(KEY_PORT, port.getText().toString());
        editor.putString(KEY_USERNAME, username.getText().toString());
        editor.putString(KEY_PASSWORD, password.getText().toString());
        editor.putBoolean(KEY_ACCEL_ENABLED, accelSwitch.isChecked());
        editor.putBoolean(KEY_LIGHT_ENABLED, lightSwitch.isChecked());
        editor.putBoolean(KEY_STEPS_ENABLED, stepCounterSwitch.isChecked());
        editor.putBoolean(KEY_MIC_ENABLED, micSwitch.isChecked());
        editor.putLong(KEY_PUBLISH_INTERVAL_MS, parsedIntervalMs);

        // Применяем изменения
        editor.apply();

        Toast.makeText(MqttSettings.this, "Settings saved", Toast.LENGTH_SHORT).show();

        // Можно сразу вернуться на главный экран
        finish();
    }
}