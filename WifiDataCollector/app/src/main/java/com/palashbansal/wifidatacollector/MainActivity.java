package com.palashbansal.wifidatacollector;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MainActivity extends AppCompatActivity {

	private static final int RESULT_SETTINGS = 1;
	WifiManager mainWifi;
	IntentFilter filter;
	List<ScanResult> scanResults;
	ArrayList<Map<String, String>> list;
	SimpleAdapter adapter;
	ListView listview;
	FloatingActionButton scanButton;
	Button createStat;
	boolean intentIsRegistered = false;
	private final Map<String, List<List<String>>> buildings = new HashMap<>();
	private Spinner buildingSpinner;
	private EditText floorText;
	private Spinner roomsSpinner;
	private Button addRoomButton;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		scanButton = (FloatingActionButton) findViewById(R.id.scan_fab);
		mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		filter = new IntentFilter();
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		this.registerReceiver(wifiEventReceiver, filter);
		intentIsRegistered = true;
		populateSpinners();

		if (!mainWifi.isWifiEnabled()) {
			Log.e("DEBUG", "turning on wifi");
			Toast.makeText(getApplicationContext(), "Enabling Wifi...",
					Toast.LENGTH_LONG).show();
			mainWifi.setWifiEnabled(true);
		} else {
			Log.e("DEBUG", "wifi is on");
		}

		addListenerOnButton();
	}

	private void populateSpinners() {
		//Make network call
		try {
			JSONObject data = new JSONObject("{\n" +
					"\"AcademicBlock\":\n" +
					"[\n" +
					"\t[\"C01\", \"C02\", \"C03\", \"Cdx\", \"Cdx-Glassroom\", \"LiftLobby\", \"Corridor-Cdx,LiftLobby\", \"Corridor-Cdx,C01\", \"Corridor-Classrooms\", \"Corridor-Foyer,Cdx\", \"Staircase-Classrooms\", \"Staircase-LiftLobby\", \"Staircase-WestSide\", \"Foyer\", \"Washroom-Boys\", \"Washroom-Girls\"]\n" +
					"]\n" +
					",\n" +
					"\"StudentCentre\":\n" +
					"[\n" +
					"\t[\"Staircase-Internal\", \"Staircase-External\", \"Balcony\", \"PoolTT\", \"Gym\", \"LiftLobby\", \"Washroom-Boys\", \"Washroom-Girls\", \"Corridor-Washrooms,PoolTT,Gym\", \"Music\", \"Art\", \"StudentCouncil\", \"AC\", \"Electronics\"]\n" +
					"]\n" +
					",\n" +
					"\"Hostel-Boys\":\n" +
					"[\n" +
					"\t[]\n" +
					"]\n" +
					",\n" +
					"\"Hostel-Girls\":\n" +
					"[\n" +
					"\t[]\n" +
					"]\n" +
					",\n" +
					"\"Library\":\n" +
					"[\n" +
					"\t[]\n" +
					"]\n" +
					",\n" +
					"\"FacultyHousing\":\n" +
					"[\n" +
					"\t[]\n" +
					"]\n" +
					"}");
			Iterator<String> keys = data.keys();
			while( keys.hasNext() ) {
				String key = keys.next();
				JSONArray floors = data.getJSONArray(key);
				ArrayList<List<String>> floorsList = new ArrayList<>();
				for(int i=0;i<floors.length();i++){
					ArrayList<String> floorList = new ArrayList<>();
					for(int j=0;j<floors.getJSONArray(i).length();j++)
						floorList.add(floors.getJSONArray(i).getString(j));
					floorsList.add(floorList);
				}
				buildings.put(key, floorsList);
				Log.d("building", key);
			}
		}
		catch (JSONException e) {
			e.printStackTrace();
		}

		buildingSpinner = (Spinner)findViewById(R.id.building_spinner);
		floorText = (EditText) findViewById(R.id.floor_text);
		roomsSpinner = (Spinner)findViewById(R.id.room_spinner);
		addRoomButton = (Button)findViewById(R.id.add_room);
		assert addRoomButton != null;
		assert roomsSpinner != null;
		assert buildingSpinner != null;
		assert floorText != null;
		buildingSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, buildings.keySet().toArray()));
		buildingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				refreshRooms();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				refreshRooms();
			}
		});
		floorText.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				//Populate room spinner
				if(keyCode == KeyEvent.KEYCODE_ENTER) {
					if (floorText.getText().toString().equals("")) return false;
					if (buildingSpinner.getSelectedItem() == null)
						Toast.makeText(MainActivity.this, "Choose a building", Toast.LENGTH_SHORT).show();
					else refreshRooms();
				}
				return false;
			}
		});
		addRoomButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(floorText.getText().toString().equals(""))floorText.setText("0");
				if(buildingSpinner.getSelectedItem()==null) {
					Toast.makeText(MainActivity.this, "Choose a building", Toast.LENGTH_SHORT).show();
					return;
				}
				LayoutInflater li = LayoutInflater.from(MainActivity.this);
				@SuppressLint("InflateParams") View promptsView = li.inflate(R.layout.new_room_prompt, null);
				android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(MainActivity.this);
				alertDialogBuilder.setView(promptsView);
				final EditText userInput = (EditText) promptsView.findViewById(R.id.server_name_input);
				alertDialogBuilder
						.setCancelable(false)
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										if(userInput.getText().toString().equals(""))dialog.cancel();
										else{
											String building = (String) buildingSpinner.getSelectedItem();
											if(!buildings.keySet().contains(building))return;
											int floor = Integer.parseInt(floorText.getText().toString());
											List<String> rooms;
											while(floor>=buildings.get(building).size())buildings.get(building).add(new ArrayList<String>());
											try {
												rooms = buildings.get(building).get(floor);
											}catch(Exception e){
												e.printStackTrace();
												rooms = new ArrayList<>();
											}
											if(rooms.contains(userInput.getText().toString()))dialog.cancel();
											else{
												rooms.add(userInput.getText().toString());
												refreshRooms();
												roomsSpinner.setSelection(rooms.size()-1);
											}
										}
									}
								})
						.setNegativeButton("Cancel",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										dialog.cancel();
									}
								});

				android.support.v7.app.AlertDialog alertDialog = alertDialogBuilder.create();
				alertDialog.show();
			}
		});
	}

	private void refreshRooms() {
		String building = (String) buildingSpinner.getSelectedItem();
		if(floorText.getText().toString().equals("")) floorText.setText("0");
		int floor = Integer.parseInt(floorText.getText().toString());
		try {
			roomsSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, buildings.get(building).get(floor)));
		}catch(Exception e){
			e.printStackTrace();
			roomsSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, new ArrayList<String>()));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private BroadcastReceiver wifiEventReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("DEBUG", "Update received!");
			if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				scanResults = mainWifi.getScanResults();
				Log.d("scan", scanResults.toString());
				list = new ArrayList<>();
				Collections.sort(scanResults, new Comparator<ScanResult>() {
					@Override
					public int compare(ScanResult lhs, ScanResult rhs) {
						return (lhs.level > rhs.level ? -1 : (lhs.level == rhs.level ? 0 : 1));
					}
				});
				list.clear();
				list = buildData(scanResults);
				updateList(context);

			}
		}
	};

	private void updateList(Context context) {
		adapter = new SimpleAdapter(context, list, R.layout.ap_list_item, new String[]{"BSSID", "Strength", "SSID"}, new int[]{R.id.BSSID, R.id.strength, R.id.SSID});
		listview = (ListView) findViewById(R.id.listView);
		assert listview != null;
		listview.setAdapter(adapter);
		adapter.notifyDataSetChanged();
		Log.d("list", list.toString());
	}

	private ArrayList<Map<String, String>> buildData(java.util.List<ScanResult> s) {
		ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
		for (ScanResult result : s) {
			list.add(putData(result.BSSID, result.SSID, result.level));
		}
		return list;
	}

	private HashMap<String, String> putData(String BSSID, String SSID, int level) {
		HashMap<String, String> item = new HashMap<String, String>();
		item.put("BSSID", BSSID);
		item.put("SSID", SSID);
		item.put("Strength", Integer.toString(level));
		return item;
	}

	public void addListenerOnButton() {
		scanButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!mainWifi.startScan()) {
					Log.e("Error", "Scanning could not start");
					Toast.makeText(getApplicationContext(), "Scanning could not start", Toast.LENGTH_SHORT).show();
				} else {
					Log.e("DEBUG", "Scanning has started...");
				}
			}
		});

//		createStat.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
////				startSendStat = new Intent(MainActivity.this, SendStat.class);
////				startSendStat.putExtra("scanResult", list);
////				startActivity(startSendStat);
//				Toast.makeText(MainActivity.this, "Call class SendStat.", Toast.LENGTH_SHORT).show();
//			}
//		});
	}

	@Override
	public void onPause() {
		super.onPause();
		if (intentIsRegistered) {
			unregisterReceiver(wifiEventReceiver);
			intentIsRegistered = false;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!intentIsRegistered) {
			registerReceiver(wifiEventReceiver, filter);
			intentIsRegistered = true;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
			case RESULT_SETTINGS:
				showUserSettings();
				break;

		}

	}

	private void showUserSettings() {
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
		alert.setTitle("Backend");
		alert.setMessage("Current URL is " + sharedPrefs.getString("prefBackend", "NULL"));

		alert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		alert.show();
	}

}
