package io.brendanmyers.rpiconf;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    final UUID uuid = UUID.fromString("815425a5-bfac-47bf-9321-c5ff980b5e11");
    final byte delimiter = 33;
    BluetoothSocket mmSocket;
    Spinner devicesSpinner;
    Button refreshDevicesButton;
    TextView ssidTextView;
    TextView pskTextView;
    Button startButton;
    TextView ipTextView;
    int readBufferPosition = 0;
    private DeviceAdapter adapter_devices;
    Button startMessagesActivity;

    private static String reverseLines(final String text) {
        String[] text_lines = text.split("\n");
        StringBuilder buff = new StringBuilder();
        for (int i = text_lines.length - 1; i >= 0; --i) {
            buff.append(text_lines[i] + "\n");
        }
        return buff.toString();
    }

    private static String toAnchorTag(final String text) {
        //takes a text of the form:
        //     received:ip-address:x.x.x.x
        // and outputs:
        //     <a href='http://[ip-address]'>[ip-address]</a>
        String[] parts = text.split(":");
        String ipComponent = parts[parts.length - 1];
        String output = "Found a Bitmi at: <a href=\"http://" + ipComponent + "\">" + ipComponent + "</a>";
        return output;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ssidTextView = (TextView) findViewById(R.id.ssid_text);
        pskTextView = (TextView) findViewById(R.id.psk_text);

        ipTextView = (TextView) findViewById(R.id.ip_address);
        ipTextView.setMovementMethod(LinkMovementMethod.getInstance());

        devicesSpinner = (Spinner) findViewById(R.id.devices_spinner);
        startMessagesActivity = (Button) findViewById(R.id.messages_button);
        startMessagesActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, MessagesActivity.class));
            }
        });

        refreshDevicesButton = (Button) findViewById(R.id.refresh_devices_button);
        startButton = (Button) findViewById(R.id.start_button);

        refreshDevicesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDevices();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ssid = ssidTextView.getText().toString();
                String psk = pskTextView.getText().toString();

                BluetoothDevice device = (BluetoothDevice) devicesSpinner.getSelectedItem();
                (new Thread(new workerThread(ssid, psk, device))).start();
            }
        });

        refreshDevices();
    }

    private void refreshDevices() {
        adapter_devices = new DeviceAdapter(this, R.layout.spinner_devices, new ArrayList<BluetoothDevice>());
        devicesSpinner.setAdapter(adapter_devices);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                adapter_devices.add(device);
            }
        }
    }

    private void writeOutput(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String currentText = Globals.getMessages();
                String reversedText = reverseLines(text);
                Globals.setMessages(reversedText + "\n" + currentText);
                if (text.contains("ip-addres")) {
                    String[] lines = text.split("\n");
                    for (int i = 0; i < lines.length; ++i) {
                        if (lines[i].contains("ip-addres")) {
                            String anchorTag = toAnchorTag(lines[i]);
                            ipTextView.setText(Html.fromHtml(anchorTag));
                            break;
                        }
                    }
                }
            }
        });
    }

    private void clearOutput() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Globals.setMessages("");
            }
        });
    }

    /*
     * TODO actually use the timeout
     */
    private void waitForResponse(InputStream mmInputStream, long timeout) throws IOException {
        int bytesAvailable;

        while (true) {
            bytesAvailable = mmInputStream.available();
            if (bytesAvailable > 0) {
                byte[] packetBytes = new byte[bytesAvailable];
                byte[] readBuffer = new byte[1024];
                mmInputStream.read(packetBytes);

                for (int i = 0; i < bytesAvailable; i++) {
                    byte b = packetBytes[i];

                    if (b == delimiter) {
                        byte[] encodedBytes = new byte[readBufferPosition];
                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                        final String data = new String(encodedBytes, "US-ASCII");

                        writeOutput("Received:" + data);

                        return;
                    } else {
                        readBuffer[readBufferPosition++] = b;
                    }
                }
            }
        }
    }

    final class workerThread implements Runnable {
        private String ssid;
        private String psk;
        private BluetoothDevice device;

        public workerThread(String ssid, String psk, BluetoothDevice device) {
            this.ssid = ssid;
            this.psk = psk;
            this.device = device;
        }

        public void run() {
            clearOutput();
            writeOutput("Starting config update.");

            writeOutput("Device: " + device.getName() + " - " + device.getAddress());

            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                if (!mmSocket.isConnected()) {
                    mmSocket.connect();
                    Thread.sleep(1000);
                }

                writeOutput("Connected.");

                OutputStream mmOutputStream = mmSocket.getOutputStream();
                final InputStream mmInputStream = mmSocket.getInputStream();

                waitForResponse(mmInputStream, -1);

                writeOutput("Sending SSID.");

                mmOutputStream.write(ssid.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream, -1);

                writeOutput("Sending PSK.");

                mmOutputStream.write(psk.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream, -1);

                mmSocket.close();

                writeOutput("Success.");

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();

                writeOutput("Failed.");
            }

            writeOutput("Done.");
        }
    }
}