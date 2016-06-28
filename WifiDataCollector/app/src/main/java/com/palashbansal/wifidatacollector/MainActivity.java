package com.palashbansal.wifidatacollector;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.palashbansal.wifidatacollector.helpers.VolleyRequestQueue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

//Most of the code here regarding fetching WiFi APs is written by Vinayak Shukl {@link: https://github.com/VinayakShukl}

public class MainActivity extends AppCompatActivity {

	private static final int RESULT_SETTINGS = 1;
	private static String BASE_URL = "http://192.168.58.21:5000";
	private static final String BUILDINGS_JSON_URL = "/Virtual_Campus/buildings.json";
	private static final String LOCATION_URL = "/Virtual_Campus/test_location";
	private static final String REPORT_URL = "/Virtual_Campus/report_location";
	private static final String SAVE_URL = "/Virtual_Campus/save_file";
	private WifiManager mainWifi;
	private IntentFilter filter;
	private List<ScanResult> scanResults;
	private ArrayList<Map<String, String>> list;
	private SimpleAdapter adapter;
	private ListView listview;
	private FloatingActionButton scanButton;
	private FloatingActionButton saveButton;
	private int lastSavedID = -1;
	private boolean intentIsRegistered = false;
	private final Map<String, List<List<String>>> buildings = new HashMap<>();
	private Spinner buildingSpinner;
	private EditText floorText;
	private Spinner roomsSpinner;
	private Button addRoomButton;
	private LinearLayout locationForm;
	private LinearLayout locationInfo;
	private FloatingActionButton modeButton;
	private boolean mode = false; //0: train, 1: test
	private boolean reporting = false; //0: train, 1: test
	private static boolean debug = false;
	private TextView locationInfoText;
	private TextView locationReportButton;
	private SharedPreferences sharedPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		sharedPref = getPreferences(Context.MODE_PRIVATE);
		lastSavedID = sharedPref.getInt("LAST ID", -1);

		locationInfoText = (TextView) findViewById(R.id.location_info_text);
		locationReportButton = (TextView) findViewById(R.id.location_report_button);
		locationInfo = (LinearLayout) findViewById(R.id.location_info);
		locationForm = (LinearLayout) findViewById(R.id.location_form);
		scanButton = (FloatingActionButton) findViewById(R.id.scan_fab);
		saveButton = (FloatingActionButton) findViewById(R.id.save_fab);
		modeButton = (FloatingActionButton) findViewById(R.id.mode_fab);
		mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		listview = (ListView) findViewById(R.id.listView);

		BASE_URL = sharedPref.getString("BASE_URL", BASE_URL);

		filter = new IntentFilter();
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		this.registerReceiver(wifiEventReceiver, filter);
		intentIsRegistered = true;

		fetchBuildingsJSON();

		if (!mainWifi.isWifiEnabled()) {
			Log.e("DEBUG", "turning on wifi");
			Toast.makeText(getApplicationContext(), "Enabling Wifi...",
					Toast.LENGTH_LONG).show();
			mainWifi.setWifiEnabled(true);
		} else {
			Log.e("DEBUG", "wifi is on");
		}

		addListenerOnButton();
		setLayoutHeight();
	}

	private void fetchBuildingsJSON() {
		VolleyRequestQueue.addToRequestQueue(new StringRequest(Request.Method.GET, BASE_URL  + BUILDINGS_JSON_URL,
						new Response.Listener<String>() {
							@Override
							public void onResponse(String response) {
								populateSpinners(response);
							}
						}, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						error.printStackTrace();
						Snackbar.make(listview, "Error contacting server: " + error.getMessage(), Snackbar.LENGTH_LONG).show();
					}
				}
				), this
		);
	}

	private void setLayoutHeight() {
		LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) locationForm.getLayoutParams();
		LinearLayout.LayoutParams params2 = (LinearLayout.LayoutParams) locationInfo.getLayoutParams();
		params2.height = params.height;
		locationInfo.setLayoutParams(params2);
	}

	private void populateSpinners(String initData) {
		try {
			JSONObject data = new JSONObject(initData);
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
		buildingSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, buildings.keySet().toArray()));
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
			roomsSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_activated_1, buildings.get(building).get(floor)));
		}catch(Exception e){
			e.printStackTrace();
			roomsSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_activated_1, new ArrayList<String>()));
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

		if (id == R.id.action_settings) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Title");
			final EditText input = new EditText(this);
			input.setText(BASE_URL);
			builder.setView(input);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					BASE_URL = input.getText().toString();
					sharedPref.edit().putString("BASE_URL", BASE_URL).apply();
					fetchBuildingsJSON();
				}
			});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			builder.show();
			return true;
		}else if(id == R.id.action_refresh_building){
			fetchBuildingsJSON();
			return true;
		}else if(id == R.id.action_debug){
			debug = !debug;
			Snackbar.make(listview, "Debug: " + debug, Snackbar.LENGTH_SHORT);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private BroadcastReceiver wifiEventReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("DEBUG", "Update received!");
			if(listview!=null&&debug)
				Snackbar.make(listview, "Recieved " + intent.getAction(), Snackbar.LENGTH_SHORT).show();
			if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				scanResults = mainWifi.getScanResults();
				if(listview!=null&&debug)
					Snackbar.make(listview, "Scan " + scanResults.size(), Snackbar.LENGTH_SHORT).show();
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
					if(listview!=null&&debug)
						Snackbar.make(listview, "Scan Started", Snackbar.LENGTH_SHORT).show();
				}
			}
		});
		saveButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String filename = "WiFiData.json";
				File root = android.os.Environment.getExternalStorageDirectory();
				File dir = new File (root.getAbsolutePath());
				dir.mkdirs();
				File file = new File(dir, filename);
				try {
					FileOutputStream f = new FileOutputStream(file, true);
					PrintWriter pw = new PrintWriter(f);
					if(buildingSpinner==null|| buildingSpinner.getSelectedItem()==null|| floorText.getText().toString().equals("")||roomsSpinner.getSelectedItem()==null) {
						Snackbar.make(listview, "No Building selected", Snackbar.LENGTH_SHORT).show();
						return;
					}
					if(scanResults==null) {
						Snackbar.make(listview, "No AP Data", Snackbar.LENGTH_SHORT).show();
						return;
					}
					String readings = serializeWifiData();
					String string = String.format(Locale.ENGLISH, "\"%d\":{\"Location\":{\"Building\":\"%s\",\"Floor\":%d,\"Room\":\"%s\",\"Timestamp\":%d},\"Readings\":{%s}},",
							++lastSavedID, buildingSpinner.getSelectedItem(), Integer.parseInt(floorText.getText().toString()), roomsSpinner.getSelectedItem(), System.currentTimeMillis(), readings.substring(0,readings.length()-1));
					SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
					SharedPreferences.Editor editor = sharedPref.edit();
					editor.putInt("LAST ID", lastSavedID);
					editor.apply();
					pw.println(string);
					pw.flush();
					pw.close();
					f.close();
					Snackbar.make(scanButton, "Saved new entry, id: " + lastSavedID, Snackbar.LENGTH_LONG).show();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Log.e("Error", "File not found. Permission error maybe");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		modeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if(reporting){
					if(buildingSpinner.getSelectedItem()==null|| floorText.getText().toString().equals("")||roomsSpinner.getSelectedItem()==null) return;
					if(scanResults==null)return;
					String readings = serializeWifiData();
					final String string = String.format(Locale.ENGLISH, "%d:{\"Location\":{\"Building\":\"%s\",\"Floor\":%d,\"Room\":\"%s\",\"Timestamp\":%d,},\"Readings\":{%s}},",
							lastSavedID, buildingSpinner.getSelectedItem(), Integer.parseInt(floorText.getText().toString()), roomsSpinner.getSelectedItem(), System.currentTimeMillis(), readings.substring(0,readings.length()-1));
					VolleyRequestQueue.addToRequestQueue(new StringRequest(Request.Method.POST, BASE_URL + REPORT_URL, new Response.Listener<String>() {
						@Override
						public void onResponse(String response) {
							if(response.toLowerCase().equals("true")) {
								reporting = false;
								changeLocationInfoVisibility(true);
								modeButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.test_tube, null));
							}else{
								Snackbar.make(v, response, Snackbar.LENGTH_SHORT).show();
							}
						}
					}, new Response.ErrorListener() {
						@Override
						public void onErrorResponse(VolleyError error) {
							Snackbar.make(v, "Network error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
							error.printStackTrace();
						}
					}){
						@Override
						protected Map<String,String> getParams(){
							Map<String,String> params = new HashMap<>();
							params.put("json", string);
							return params;
						}
					}, MainActivity.this);
					return;
				}
				mode = !mode;
				changeLocationInfoVisibility(mode);
				if(mode){ //test location
					new Handler().postDelayed(new Runnable() {
						Runnable object;
						@Override
						public void run() {
							if(!mode) return;
							object = this;
							if(scanResults!=null){
								VolleyRequestQueue.addToRequestQueue(new StringRequest(Request.Method.POST, BASE_URL + LOCATION_URL, new Response.Listener<String>() {
									@Override
									public void onResponse(String response) {
										locationInfoText.setText(response);
										new Handler().postDelayed(object, 2000);
									}
								}, new Response.ErrorListener() {
									@Override
									public void onErrorResponse(VolleyError error) {
										error.printStackTrace();
										locationInfoText.setText("Unable to reach server");
										new Handler().postDelayed(object, 2000);
									}
								}){
									@Override
									protected Map<String,String> getParams(){
										Map<String,String> params = new HashMap<>();
										for(ScanResult scanResult: scanResults)
											params.put(scanResult.BSSID, String.valueOf(scanResult.level));
										return params;
									}
								}, MainActivity.this);
							}
						}
					}, 1);
				}
			}
		});
		locationReportButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				changeLocationInfoVisibility(false);
				modeButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.upload, null));
				reporting = true;
			}
		});
		saveButton.setLongClickable(true);
		saveButton.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				String filename = "WiFiData.json";
				File root = android.os.Environment.getExternalStorageDirectory();
				File dir = new File (root.getAbsolutePath());
				dir.mkdirs();
				File file = new File(dir, filename);
				try {
					FileInputStream f = new FileInputStream(file);
					final byte[] data = new byte[(int) file.length()];
					int a = f.read(data);
					if(a>0){
						Log.d("SAVE", BASE_URL + SAVE_URL);
						VolleyRequestQueue.addToRequestQueue(new StringRequest(Request.Method.POST, BASE_URL + SAVE_URL, new Response.Listener<String>() {
							@Override
							public void onResponse(String response) {
								if (response.equals("true")) {
									Snackbar.make(v, "File sent to server.", Snackbar.LENGTH_SHORT).show();
								}
							}
						}, new Response.ErrorListener() {
							@Override
							public void onErrorResponse(VolleyError error) {
								error.printStackTrace();
								Snackbar.make(v, "Error contacting server: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
							}
						}){
							@Override
							protected Map<String,String> getParams(){
								Map<String,String> params = new HashMap<>();
								try {
									params.put("device_id", ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId());
									params.put("raw_data", new String(data, "UTF-8"));
								} catch (UnsupportedEncodingException e) {
									e.printStackTrace();
								}
								return params;
							}
						}, MainActivity.this);
					}
					f.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Log.e("Error", "File not found. Permission error maybe");
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			}
		});
	}

	private void changeLocationInfoVisibility(boolean b) {
		if(b){
			locationInfo.setVisibility(View.VISIBLE);
			locationForm.setVisibility(View.GONE);
		}else{
			locationInfo.setVisibility(View.GONE);
			locationForm.setVisibility(View.VISIBLE);
		}
	}

	private String serializeWifiData() {
		String readings = "";
		for(ScanResult scanResult: scanResults){
			readings+=String.format(Locale.ENGLISH, "\"%s\":%d,", scanResult.BSSID, scanResult.level);
		}
		return readings;
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
