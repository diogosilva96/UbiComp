package com.example.diogo.ubichair;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.UpdateAppearance;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothSocket _btSocket;
    BluetoothDevice _Device = null;
    int lastSentDesiredTemp = 0 ;
    boolean sendMsg = false;
    

    final byte delimiter = 59; //caracter ; em ASCII
    int readBufferPosition = 0;

 //testar o state
    public void sendBtMsg(String msg2send){
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
        try {

            _btSocket = _Device.createRfcommSocketToServiceRecord(uuid);
            if (!_btSocket.isConnected()){
                Log.e("UbiChair","connected to socket");
                _btSocket.connect();//liga-se ao socket

            }
            String msg = msg2send;
            OutputStream mmOutputStream = _btSocket.getOutputStream();
            mmOutputStream.write(msg.getBytes());//envia a msg para o rpi

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        final Handler delayedHandler = new Handler();
        final Handler handler = new Handler();
        final ImageView _opImage = (ImageView)findViewById(R.id.operationImage);
        final Button _ChangeTempButton = (Button)findViewById(R.id.okButton);
        final ProgressBar _actualTempBar = (ProgressBar)findViewById(R.id.actualTempBar);
        final SeekBar _desiredTempBar = (SeekBar)findViewById(R.id.desiredTempBar);
        final TextView _actualTempText = (TextView)findViewById(R.id.actualTempText);
        final TextView _desiredTempText = (TextView)findViewById(R.id.desiredTempText);
        final TextView _opTypeText = (TextView)findViewById(R.id._opType);

        final ChairStateUpdater chairStateUpdater = new ChairStateUpdater(_opTypeText,_opImage);
        final TemperatureUpdater _actualTemperatureUpdater = new TemperatureUpdater(_actualTempText,_actualTempBar,chairStateUpdater);
        final TemperatureUpdater _desiredTemperatureUpdater = new TemperatureUpdater(_desiredTempText,_desiredTempBar,chairStateUpdater);
        chairStateUpdater.UpdateView(_actualTemperatureUpdater.getTemperature(),lastSentDesiredTemp);
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        final class CommunicationThread implements Runnable {

            private String btMsg;

            public CommunicationThread(String msg) {
                btMsg = msg;
            }
            public CommunicationThread(){
                btMsg = "";
            }
            public void setBtMsg(String msg){
                btMsg = msg;
            }

            public void run() {
                while (true){
                    if(sendMsg) {
                        setBtMsg("desiredtemp="+lastSentDesiredTemp);
                        sendBtMsg(btMsg);
                        while (!Thread.currentThread().isInterrupted()) {
                            int bytesAvailable;
                            boolean workDone = false;
                            try {
                                final InputStream mmInputStream;
                                mmInputStream = _btSocket.getInputStream();
                                bytesAvailable = mmInputStream.available();
                                if (bytesAvailable > 0) {
                                    byte[] packetBytes = new byte[bytesAvailable];
                                    Log.e("UbiChair recv bt", "bytes available");
                                    byte[] readBuffer = new byte[1024];
                                    mmInputStream.read(packetBytes);

                                    for (int i = 0; i < bytesAvailable; i++) {
                                        byte b = packetBytes[i];
                                        if (b == delimiter) {
                                            chairStateUpdater.setState(1);
                                            byte[] encodedBytes = new byte[readBufferPosition];
                                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                            final String data = new String(encodedBytes, "UTF-8");
                                            readBufferPosition = 0;
                                            //The variable data now contains our full command
                                            handler.post(new Runnable() {
                                                public void run() {
                                                    Toast.makeText(MainActivity.this, "Received data: " + data, Toast.LENGTH_LONG).show();
                                                    String[] aux = data.split("=");
                                                    int auxActualTemp = Integer.parseInt(aux[1]);
                                                    _actualTemperatureUpdater.setTemperature(auxActualTemp);
                                                    chairStateUpdater.UpdateView(_actualTemperatureUpdater.getTemperature(), lastSentDesiredTemp);
                                                }
                                            });
                                            workDone = true;
                                            break;
                                        } else {
                                            readBuffer[readBufferPosition++] = b;
                                        }
                                    }
                                    if (workDone == true) {
                                        _btSocket.close();
                                        sendMsg = false;
                                        Log.e("UbiChair", "socket closed");
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        };

        final CommunicationThread communicationThread;
        communicationThread = new CommunicationThread();



   MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                delayedHandler.postDelayed(this, 10000);//update de 10 em 10 segundos
                delayedHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(chairStateUpdater.getState() != 0){
                            sendMsg = true;
                        }
                    }
                });
            }
        });

        _ChangeTempButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on change temperature button click
                if (mBluetoothAdapter.isEnabled()) {
                    lastSentDesiredTemp = _desiredTemperatureUpdater.getTemperature();
                    if (chairStateUpdater.getState() == 0) {
                        sendMsg = true;
                        new Thread(communicationThread).start();

                    } else {
                        sendMsg = true;
                    }
                } else {
                    Toast.makeText(MainActivity.this,"Please enable Bluetooth!",Toast.LENGTH_LONG).show();
                }

         /*       (new Thread(new CommunicationThread("desiredtemp="+lastSentDesiredTemp))).start(); //envia o valor da temperatura
                sendMsg = true;*/


            }
        });

        if (mBluetoothAdapter != null) {//o dispositivo suporta bluetooth
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);//pede para ligar bluetooth
            }
        } else {
            _opTypeText.setText("Error no bluetooth connection");
            Toast.makeText(MainActivity.this,"Your device does not support bluetooth!",Toast.LENGTH_LONG).show();
        }



        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("raspberrypi")) //nome do dispositivo
                {
                    Log.e("Device connected: ", device.getName());
                    _Device = device;
                    break;
                }
            }
        }


    }
    public class ChairStateUpdater{
        private TextView textView;
        private ImageView image;
        private int state;

        public ChairStateUpdater(TextView tv, ImageView Image){
            textView = tv;
            image = Image;
            image.setVisibility(View.INVISIBLE);
            state = 0;
        }

        public void UpdateView(int actualTemperature, int desiredTemperature) {
            if (state == 1) {
                image.setVisibility(View.VISIBLE);
                if (actualTemperature > desiredTemperature) {
                    textView.setText("Cooling");
                    textView.setTextColor(Color.BLUE);
                    image.setImageResource(R.drawable.cooling);
                } else if (actualTemperature < desiredTemperature) {
                    textView.setText("Heating");
                    textView.setTextColor(Color.RED);
                    image.setImageResource(R.drawable.heating);
                } else {
                    textView.setText("Temperature is fine");
                    textView.setTextColor(Color.GREEN);
                    image.setVisibility(View.INVISIBLE);
                    //image.setImageResource(R.drawable.ok);
                }
            } else {
                textView.setText("No connection to UbiChair");
                textView.setTextColor(Color.BLACK);
                image.setVisibility(View.INVISIBLE);
            }
        }

        public int getState(){
            return state;
        }

        public void setState(int state){
            this.state = state;
        }
    }


    public class TemperatureUpdater {
        private TextView TemperatureText; //texto que apresenta a temperatura
        private ProgressBar TemperatureBar; //barra da temperatura (progressbar ou seekbar conforme o tipo)
        private int Temperature; // temperatura pode ser atual ou desired
        private String description; //Descrição do tipo de temperatura (actual ou desired) actual vem da cadeira, desired é alterada nesta app
        private ChairStateUpdater CSU;

        public TemperatureUpdater(TextView tv, ProgressBar sb, ChairStateUpdater csu) {
            description = "actualTemperature";
            TemperatureText = tv;
            TemperatureBar = sb;
            TemperatureBar.setMax(25);
            CSU = csu;

            if (TemperatureBar instanceof SeekBar) { //se for seekbar sabemos que é a desired temp e necessita de um listener quando o utilizador mexe nesta
                description = "desiredTemperature";
                ((SeekBar) TemperatureBar).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        UpdateTemperature();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });


            }

            TemperatureText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (TemperatureBar.getProgress() > 7 && TemperatureBar.getProgress() <= 14) {
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                    }
                    if (TemperatureBar.getProgress() <= 7) {
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN);
                    }
                    if (TemperatureBar.getProgress() > 14) {
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });
            Temperature = 20;
            setTemperature(Temperature);
        }

        public int getTemperature() {
            return Temperature;
        }

        public TextView getTemperatureText() {
            return TemperatureText;
        } //se necessário

        public ProgressBar getTemperatureBar() {
            return TemperatureBar;
        } // se necessário

        public void setTemperature(int temp) {
            Temperature = temp;
            TemperatureBar.setProgress(Temperature - 15);
            UpdateTemperature();
        }


        public void UpdateTemperature() {
            if (CSU.getState() == 0 && description == "actualTemperature") {
                Temperature = 15;
                TemperatureBar.setProgress(Temperature - 15);
                TemperatureText.setText("No connection");
                TemperatureBar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
            } else {

                Temperature = TemperatureBar.getProgress() + 15;
                TemperatureText.setText(Temperature + "ºC");

            }
        }
    }
}
