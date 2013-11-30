package com.crystallinegreen.wiimote4ouya;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final int TYPE_L2CAP = 3;
	
    private static final int CONTROL_CHANNEL = 0x11;
    private static final int DATA_CHANNEL = 0x13;

	private static final int REQUEST_ENABLE_BT = 1;
	
	private ArrayList<Wiimote> pairedWiiMotes = new ArrayList<Wiimote>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
				
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter != null) {
								
			if (mBluetoothAdapter.isEnabled()) {
			 
				bloothoothConnect(mBluetoothAdapter);
				
			} else {				
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);				
			}
		}		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if(requestCode==REQUEST_ENABLE_BT && resultCode==RESULT_OK){
			
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();			
			if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {			
				bloothoothConnect(mBluetoothAdapter);			
			}
		}		
	}
	
	private void bloothoothConnect(final BluetoothAdapter mBluetoothAdapter){
		
		Intent discoverableIntent = new	Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
		startActivity(discoverableIntent);
		
		
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(new BroadcastReceiver() {
		    public void onReceive(Context context, Intent intent) {
		        String action = intent.getAction();
		        // When discovery finds a device
		        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
		            // Get the BluetoothDevice object from the Intent
		            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		            // Add the name and address to an array adapter to show in a ListView
			    	if(isWiiMote(device)){
			    		mBluetoothAdapter.cancelDiscovery();
			    		connectWiiMote(device);			    		
			    	}
		        }
		    }
		}, filter); // Don't forget to unregister during onDestroy
		
		mBluetoothAdapter.startDiscovery();
	}
	
	private boolean isWiiMote(BluetoothDevice device){
    	String name = device.getName(); 		    	
    	return name.equalsIgnoreCase("Nintendo RVL-CNT-01");    	
	}

	private void connectWiiMote(final BluetoothDevice device){
		final int tickInterval = 30;		
		Wiimote nWiiMote = new Wiimote(device, tickInterval){

			@Override
			void onButtonDown(int buttonId, int eventType) {
				// TODO Auto-generated method stub
				
			}

			@Override
			void onAxisUpdate(float xAxis, float yAxis, float zAxis) {
				// TODO Auto-generated method stub
				
			}
			
		};
		
		pairedWiiMotes.add(nWiiMote);
	}
	
	private abstract class Wiimote implements Runnable{
		
		private BluetoothDevice device;
	    private BluetoothSocket controlSocket;
	    private BluetoothSocket dataSocket;
		private long tickInterval;
		private byte[] buffer = new byte[1024];
		private InputStream iStream;
		private OutputStream oStream;
		
		int dataReportingMode = 0x12;
		int continuousDiscrete = 0x04;
		int mode = 0x31;
		
        private byte[] m_setReportMode = new byte[] {
                0x52, 0x12, 0x00, 0x32  
        };
        private byte m_LEDstate;
        private final byte[] m_setLEDStatus = new byte[] {
                0x52, 0x11, 0x00        
        };

		private String st = new String();
		private View view;
		private Handler debugOut;
		
		Wiimote(BluetoothDevice device, long tickInterval) {
			this.device = device;
			this.tickInterval = tickInterval;
			
			initView();
			
			debugOut.dispatchMessage(Message.obtain(debugOut, 0, "Wiimote Found. Connecting..."));
			
			connect(device);
						
			Thread nThread = new Thread(this);
			nThread.start();	
			
		}

		abstract void onButtonDown(int buttonId, int eventType);
		
		abstract void onAxisUpdate(float xAxis, float yAxis, float zAxis);
		
		@SuppressLint("NewApi")
		@Override
		public void run() {
			
			try {
				
				initSockets();
			
				
				
				while(dataSocket.isConnected()){
					
					iStream.read(buffer);
					
					st = "";
					for(int i = 0;i<32;i++){
						st += Byte.toString(buffer[i]);
					}
					
					// do stuff here
					debugOut.dispatchMessage(Message.obtain(debugOut, 2, st));
					
					Thread.sleep(tickInterval);									
				}
			
			} catch (final IOException e2) {
				closeSockets();
				debugOut.dispatchMessage(Message.obtain(debugOut, 1, e2.toString()));
			} catch (final InterruptedException e) {
				closeSockets();
				debugOut.dispatchMessage(Message.obtain(debugOut, 1, e.toString()));
				return;
			}
			
		}
		
		private void initSockets() throws IOException{
			
			if(!controlSocket.isConnected()){
				// blocks here!
				controlSocket.connect();
			}
			oStream = controlSocket.getOutputStream();
			
			oStream.write(m_setReportMode);
			oStream.flush();			
			
			if(!dataSocket.isConnected()){
				// blocks here!
				dataSocket.connect();
			}
			iStream = dataSocket.getInputStream();
			
			try {
				setLEDs(true, false, true, false, false);
			} catch (Exception e) {
				debugOut.dispatchMessage(Message.obtain(debugOut, 1, e.toString()));
			}
			
			debugOut.dispatchMessage(Message.obtain(debugOut, 0, "connected"));
		}
		
		private void closeSockets(){			
			try {
				controlSocket.close();
				dataSocket.close();
			} catch (IOException e) {
				return;
			}
		}
		
	    private BluetoothSocket createL2CAPBluetoothSocket(BluetoothDevice device, final int channel) {
	        int type = TYPE_L2CAP; // L2CAP protocol
	        int fd = -1; // Create a new socket
	        boolean auth = false; // No authentication
	        boolean encrypt = false; // Not encrypted

	        try {
	            Constructor<BluetoothSocket> constructor = BluetoothSocket.class.getDeclaredConstructor(int.class,
	                    int.class, boolean.class, boolean.class, BluetoothDevice.class, int.class, ParcelUuid.class);
	            constructor.setAccessible(true);
	            BluetoothSocket clientSocket = (BluetoothSocket) constructor.newInstance(type, fd, auth, encrypt, device,
	                    channel, null);
	            return clientSocket;
	        } catch (final Exception e) {
	        	debugOut.dispatchMessage(Message.obtain(debugOut, 1, e.toString()));
	            return null;
	        }
	    }

	    private void connect(BluetoothDevice device) {
	        try {
	            controlSocket = createL2CAPBluetoothSocket(device, CONTROL_CHANNEL);        
	            //controlSocket.connect();
	            
	            dataSocket = createL2CAPBluetoothSocket(device, DATA_CHANNEL);
	            //dataSocket.connect();
	            
	            // open transmit & receive threads for input and output streams appropriately

	        } catch (final Exception e) {
	        	debugOut.dispatchMessage(Message.obtain(debugOut, 1, e.toString()));
	        }
	    }	
	    
	    private void setLEDs(boolean l1, boolean l2, boolean l3, boolean l4, boolean rumble) throws Exception {
            m_LEDstate = (byte)
                    ((l1 ? 0x10 : 0x00) |
                    (l2 ? 0x20 : 0x00) |
                    (l3 ? 0x40 : 0x00) |
                    (l4 ? 0x80 : 0x00));
           
            updateLEDStates(rumble);
	    }	    
	    
        private void updateLEDStates(boolean rumble) throws IOException {
            m_setLEDStatus[2] = (byte)((m_LEDstate & 0xf0) | (rumble ? 0x01 : 0x00));
            oStream.write(m_setLEDStatus);
            oStream.flush();
        }

		
		private void initView(){
			view = getLayoutInflater().inflate(R.layout.wiimote_data, null, false);
			
			final TextView nameTv = (TextView) view.findViewById(R.id.wiimote_name);
			final TextView dataTv = (TextView) view.findViewById(R.id.wiimote_data_recieved);
			final ViewGroup msgLog = (ViewGroup) view.findViewById(R.id.wiimote_messageLog);			
			
			nameTv.setText(device.getName());
			
			final ArrayList<ViewGroup> v1 = new ArrayList<ViewGroup>();
			final ArrayList<View> v2 = new ArrayList<View>();
			final Runnable addViewToLog = new Runnable(){

			@Override
			public void run() {
				synchronized(v1){
					while(v1.size()>0){
						v1.remove(0).addView(v2.remove(0));
					}
				}
			}};
				
			final ArrayList<TextView> t1 = new ArrayList<TextView>();
			final ArrayList<String> t2 = new ArrayList<String>();
			final Runnable setText = new Runnable(){

			@Override
			public void run() {
				synchronized(t1){
					t1.remove(0).setText(t2.remove(0));
				}
			}};
							
				
			debugOut = new Handler(Looper.getMainLooper(),new Handler.Callback() {
								
				@Override
				public boolean handleMessage(Message msg) {
					if(msg.what==0){				        	    
			        	TextView nTv = new TextView(MainActivity.this);
			        	nTv.setText(msg.obj.toString());
			        	synchronized(v1){v1.add(msgLog);v2.add(nTv);}
			        	msgLog.post(addViewToLog);
			        } else if(msg.what==1){
			        	TextView nTv = new TextView(MainActivity.this);
			        	nTv.setText("error: " + msg.obj.toString());
			        	synchronized(v1){v1.add(msgLog);v2.add(nTv);}
			        	msgLog.post(addViewToLog);
			        } else if(msg.what==2){
			        	synchronized(t1){t1.add(dataTv);t2.add((String) msg.obj);}
			        	debugOut.post(setText);
			        } else {
			        	TextView nTv = new TextView(MainActivity.this);
			        	nTv.setText("Unknown: " + msg.obj.toString());
			        	synchronized(v1){v1.add(msgLog);v2.add(nTv);}
			        	msgLog.post(addViewToLog);
			        }
					return true;
				}
			
				});
				
				
			((ViewGroup) findViewById(R.id.main)).addView(view);
			
		}
		
	}    
    
    
 
    
}
