package packt.com.androidbleserviceexplorer;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLEPackt";

    Button startScanningButton;
    Button stopScanningButton;
    ListView deviceListView;

    ArrayAdapter<String> listAdapter;
    ArrayList<BluetoothDevice> deviceList;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final String HEART_RATE_SERVICE_ID = "180d";
    public final String HEART_RATE_MEASUREMENT_ID = "2a37";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceListView = (ListView) findViewById(R.id.deviceListView);
        startScanningButton = (Button) findViewById(R.id.StartScanButton);
        stopScanningButton = (Button) findViewById(R.id.StopScanButton);
        stopScanningButton.setVisibility(View.INVISIBLE);

        listAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1);
        deviceList = new ArrayList<>();
        deviceListView.setAdapter(listAdapter);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopScanning();
            }
        });

        initialiseBluetooth();

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                stopScanning();
                listAdapter.clear();
                BluetoothDevice device = deviceList.get(position);
                device.connectGatt(MainActivity.this, true, gattCallback);
            }
        });

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if(result.getDevice() != null) {
                if(!isDuplicate(result.getDevice())) {
                    synchronized (result.getDevice()) {
                        String itemDetail = result.getDevice().getName() == null ? result.getDevice().getAddress() :
                            result.getDevice().getName();
                        listAdapter.add(itemDetail);
                        deviceList.add(result.getDevice());
                    }
                }
            }
        }
    };

    private boolean isDuplicate(BluetoothDevice device){
        for(int i = 0; i < listAdapter.getCount(); i++) {
            String addedDeviceDetail = listAdapter.getItem(i);
            if(addedDeviceDetail.equals(device.getAddress()) || addedDeviceDetail.equals(device.getName())) {
                return true;
            }
        }
        return false;
    }

    protected BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_CONNECTED");
                boolean discoverServicesOk = gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_DISCONNECTED");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered()");
            final List<BluetoothGattService> services = gatt.getServices();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for(int i = 0; i < services.size(); i++) {
                        BluetoothGattService service = services.get(i);
                        Log.i(TAG, "onServicesDiscovered() :: "+ service.getUuid().toString());
                        StringBuffer buffer = new StringBuffer(services.get(i).getUuid().toString());
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        for(int j = 0; j < characteristics.size(); j++) {
                            buffer.append("\n");
                            buffer.append("Characteristic:"+characteristics.get(j).getUuid().toString());
                            if (buffer.toString().contains(HEART_RATE_SERVICE_ID)) {
                                if (characteristics.get(j).getUuid().toString().contains(HEART_RATE_MEASUREMENT_ID)) {
                                    System.out.println("Reading Char ID::"+characteristics.get(j).getUuid().toString());
                                    gatt.setCharacteristicNotification(characteristics.get(j), true);
                                }
                            }

                        }
                        listAdapter.add(buffer.toString());
                    }
                }
            });

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().toString().contains(HEART_RATE_MEASUREMENT_ID)) {

                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                }
                final int heartRate = characteristic.getIntValue(format, 1);
                Log.d(TAG, String.format("Received heart rate: %d", heartRate));
                // Write a message to the database
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference myRef = database.getReference("heartrate");

                myRef.setValue(new Integer(heartRate).toString());

            }
        }
    };

    private void initialiseBluetooth() {
        bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public void startScanning() {
        listAdapter.clear();
        deviceList.clear();
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        if (bluetoothLeScanner == null) {
            initialiseBluetooth();
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                bluetoothLeScanner.startScan(leScanCallback);
            }
        });
    }

    public void stopScanning() {
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        });
    }
}
