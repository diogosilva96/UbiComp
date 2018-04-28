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
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;

    final byte delimiter = 59; //caracter ; em ASCII
    int readBufferPosition = 0;


    public void sendBtMsg(String msg2send){
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
        try {

            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            if (!mmSocket.isConnected()){
                mmSocket.connect();//liga-se ao socket
            }

            String msg = msg2send;
            OutputStream mmOutputStream = mmSocket.getOutputStream();
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
        final Handler handler = new Handler();
        final TextView myLabel = (TextView)findViewById(R.id.btText);
        final Button _ChangeTempButton = (Button)findViewById(R.id.okButton);
        final ProgressBar _actualTempBar = (ProgressBar)findViewById(R.id.actualTempBar);
        final SeekBar _desiredTempBar = (SeekBar)findViewById(R.id.desiredTempBar);
        final TextView _actualTempText = (TextView)findViewById(R.id.actualTempText);
        final TextView _desiredTempText = (TextView)findViewById(R.id.desiredTempText);
        final TextView _opTypeText = (TextView)findViewById(R.id._opType);
        final TemperatureUpdater _desiredTemperatureUpdater = new TemperatureUpdater(_desiredTempText,_desiredTempBar);
        _desiredTemperatureUpdater.setTemperature(29);
        final TemperatureUpdater _actualTemperatureUpdater = new TemperatureUpdater(_actualTempText,_actualTempBar);
        _actualTemperatureUpdater.setTemperature(28);
        UpdateViewByChairState(_opTypeText,_actualTemperatureUpdater.getTemperature(),_desiredTemperatureUpdater.getTemperature());

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final class workerThread implements Runnable {

            private String btMsg;

            public workerThread(String msg) {
                btMsg = msg;
            }

            public void run()
            {
                sendBtMsg(btMsg);
                while(!Thread.currentThread().isInterrupted())
                {
                    int bytesAvailable;
                    boolean workDone = false;

                    try {



                        final InputStream mmInputStream;
                        mmInputStream = mmSocket.getInputStream();
                        bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {

                            byte[] packetBytes = new byte[bytesAvailable];
                            Log.e("UbiChair recv bt","bytes available");
                            byte[] readBuffer = new byte[1024];
                            mmInputStream.read(packetBytes);

                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "UTF-8");
                                    readBufferPosition = 0;


                                    //The variable data now contains our full command
                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            myLabel.setText(data);
                                            String[] aux = data.split("=");
                                            int auxActualTemp = Integer.parseInt(aux[1]); //fazer listener para actualtemperature change
                                            _actualTemperatureUpdater.setTemperature(auxActualTemp);//testar
                                            //_actualTempText.setText(""+_actualTemperature+"");
                                            //_actualTempBar.setProgress(_actualTemperature -15);

                                        }
                                    });

                                    workDone = true;
                                    break;


                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }

                            if (workDone == true){
                                mmSocket.close();
                                break;
                            }

                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        };

        _ChangeTempButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on change temperature button click
                UpdateViewByChairState(_opTypeText,_actualTemperatureUpdater.getTemperature(),_desiredTemperatureUpdater.getTemperature());
                Toast.makeText(MainActivity.this,
                        "desiredtemp="+_desiredTemperatureUpdater.getTemperature(), Toast.LENGTH_LONG).show();//mostra o que foi enviado no socket bt
                (new Thread(new workerThread("desiredtemp="+_desiredTemperatureUpdater.getTemperature()))).start(); //envia o valor da temperatura


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
                    mmDevice = device;
                    break;
                }
            }
        }


    }
    public void UpdateViewByChairState(TextView textView, int actualTemperature, int desiredTemperature){
        if(actualTemperature > desiredTemperature){
            textView.setText("Cooling");
            textView.setTextColor(Color.BLUE);
        } else if (actualTemperature < desiredTemperature){
            textView.setText("Heating");
            textView.setTextColor(Color.RED);
        } else {
            textView.setText("Temperature is fine");
            textView.setTextColor(Color.GREEN);
        }
    }


    final class TemperatureUpdater{
        private TextView TemperatureText;
        private ProgressBar TemperatureBar;
        private int Temperature;
        public TemperatureUpdater(TextView tv, ProgressBar sb){
            Temperature = 20;
            TemperatureText=tv;
            TemperatureBar = sb;
            TemperatureBar.setMax(25);
            TemperatureBar.setProgress(Temperature -15);

            if (TemperatureBar instanceof SeekBar){
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
                    if(TemperatureBar.getProgress()>7 && TemperatureBar.getProgress()<=14){
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
                    }
                    if(TemperatureBar.getProgress()<=7){
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
                    }
                    if(TemperatureBar.getProgress()>14){
                        TemperatureBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            setTemperature(Temperature);
        }

        public int getTemperature(){
            return Temperature;
        }
        public TextView getTemperatureText(){
            return TemperatureText;
        } //se necessário
        public ProgressBar getTemperatureBar(){
            return TemperatureBar;
        } // se necessário
        public void setTemperature(int temp){
            Temperature = temp;
            TemperatureBar.setProgress(Temperature -15);
            UpdateTemperature();
        }

        public void UpdateTemperature(){
            Temperature = TemperatureBar.getProgress()+15;
            TemperatureText.setText(""+Temperature+"");

        }
    }

}
