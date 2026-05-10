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

public class MqttSettings extends AppCompatActivity {

    private EditText broker, port, topic, username, password;
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
        connect = findViewById(R.id.btnConnect);

        // Загружаем ВСЕ сохраненные настройки
        loadSavedSettings();
    }

    /**
     * Загружает все сохраненные настройки из SharedPreferences
     */
    private void loadSavedSettings() {
        SharedPreferences sharedPref = this.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);

        // Загружаем Broker и Port (их не было в загрузке!)
        String mqttBroker = sharedPref.getString("Broker", "");
        String mqttPort = sharedPref.getString("Port", "");
        String mqttTopic = sharedPref.getString("Topic", "");
        String mqttUsername = sharedPref.getString("Username", "");
        String mqttPassword = sharedPref.getString("Password", "");

        // Устанавливаем значения в поля ввода
        broker.setText(mqttBroker);
        port.setText(mqttPort);
        topic.setText(mqttTopic);
        username.setText(mqttUsername);
        password.setText(mqttPassword);

        Log.d("MqttSettings", "Loaded settings - Broker: " + mqttBroker + ", Port: " + mqttPort);
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

        SharedPreferences sharedPref = this.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        mqttTopic = topic.getText().toString();

        // Сохраняем ВСЕ поля
        editor.putString("Topic", mqttTopic);
        editor.putString("serverURI", serverURI);
        editor.putString("Broker", broker.getText().toString());
        editor.putString("Port", port.getText().toString());
        editor.putString("Username", username.getText().toString());
        editor.putString("Password", password.getText().toString());

        // Применяем изменения
        editor.apply();

        Toast.makeText(MqttSettings.this, "Settings saved", Toast.LENGTH_SHORT).show();

        // Можно сразу вернуться на главный экран
        finish();
    }
}