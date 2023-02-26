package com.example.pigeon_esp;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.BleScanListener;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.listeners.WiFiScanListener;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.flutter.Log;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugins.FlutterESP;
import rainmaker.EspRainmakerUserMapping;
import rmaker_claim.EspRmakerClaim;


public class MainActivity extends FlutterActivity {
	ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getContext());
	HashMap<BluetoothDevice, String> bluetoothDevices = new HashMap<>();
	FlutterESP.Result<String> bleConnectResult;
	private int dataCount = 0;
	private StringBuilder csrData = new StringBuilder();

	class EspAPI implements FlutterESP.EspFlutterProvision {
		@Override
		public void scanDevices(@NonNull String prefix, FlutterESP.Result<List<String>> result) {
			List<String> devices = new ArrayList<>();
			bluetoothDevices.clear();
			if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				result.error(new Exception("Bluetooth Scan/Location Permission not available"));
			} else {
				Log.d("Bluetooth", "Starting Bluetooth Scan......");
				bluetoothDevices.clear();
				provisionManager.searchBleEspDevices(prefix, new BleScanListener() {
					@Override
					public void scanStartFailed() {
						result.error(new Throwable("Scan Failed"));
					}

					@Override
					public void onPeripheralFound(BluetoothDevice device, ScanResult scanResult) {
						boolean deviceExists = false;
						String serviceUuid = "";

						if (scanResult.getScanRecord().getServiceUuids() != null && scanResult.getScanRecord().getServiceUuids().size() > 0) {
							serviceUuid = scanResult.getScanRecord().getServiceUuids().get(0).toString();
						}

						if (bluetoothDevices.containsKey(device)) {
							deviceExists = true;
						}

						if (!deviceExists) {
							bluetoothDevices.put(device, serviceUuid);
						}
					}

					@Override
					public void scanCompleted() {
						Log.d("Bluetooth", "Bluetooth Scan Done.");
						for (Map.Entry<BluetoothDevice, String> element : bluetoothDevices.entrySet()) {
							BluetoothDevice d = element.getKey();
							String uuid = element.getValue();
							if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
							}
							devices.add(d.getName());
						}
						result.success(devices);
					}

					@Override
					public void onFailure(Exception e) {
						result.error(new Throwable("Scan Failure " + e.getMessage()));
					}
				});
			}
		}

		//----------------------------------------Bluetooth Connect-------------------------------------------------

		@Override
		public void connectBleDevice(@NonNull String deviceName, FlutterESP.Result<String> result) {
			for (Map.Entry<BluetoothDevice, String> element : bluetoothDevices.entrySet()) {
				BluetoothDevice d = element.getKey();
				String uuid = element.getValue();
				if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
					Log.d("Bluetooth Connect", "Bluetooth connect ki permission nai hai");
				}
				if (Objects.equals(d.getName(), deviceName)) {
					provisionManager.createESPDevice(
							ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1
					);
					provisionManager.getEspDevice().connectBLEDevice(d, uuid);
					Log.d("Bluetooth", "ESP Bluetooth status : " + provisionManager.getEspDevice().getDeviceName());
				}
			}
			bleConnectResult = result;
		}

		//-----------------------------------------------CLAIMING---------------------------------------------------------------

		@Override
		public void startClaim(FlutterESP.Result<String> result) {
			Log.d("Claim Start", "Claim Start Request");
			EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
					.build();

			EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimStart;
			EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
					.setMsg(msgType)
					.setCmdPayload(payloadBuf)
					.build();
			provisionManager.getEspDevice().sendDataToCustomEndPoint("rmaker_claim", payload.toByteArray(), new ResponseListener() {

				@Override
				public void onSuccess(byte[] returnData) {
					Log.d("Claim Start", "Successfully sent claiming start command");
					processClaimingStartResponse(returnData, result);
				}

				@Override
				public void onFailure(Exception e) {
					result.error(new Throwable("Claim Starting failed : " + e.getMessage()));
					Log.e("Claim Start", "Failed to start claiming");
					e.printStackTrace();
				}
			});

		}

		private void processClaimingStartResponse(byte[] responseData, FlutterESP.Result<String> result) {
			try {
				EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.parseFrom(responseData);
				EspRmakerClaim.RespPayload response = payload.getRespPayload();

				if (response.getStatus() == EspRmakerClaim.RMakerClaimStatus.Success) {
					String data = response.getBuf().getPayload().toStringUtf8();
					result.success(data);

				} else {
					result.error(new Throwable("Process Claiming Start Response Failed Status " + response.getStatus().toString()));
					Log.e("Claim Start", "Failed to start claiming");
				}

			} catch (InvalidProtocolBufferException e) {
				result.error(new Throwable("Process Claiming Start Response Failure" + e.getMessage()));
			}
		}

		@Override
		public void getCSR(@NonNull String node_id, FlutterESP.Result<String> result) {
			Log.e("Send Claim Init Request", "Claim Init Request");
			ByteString byteString = ByteString.copyFromUtf8(node_id);
			EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
					.setOffset(0)
					.setTotalLen(byteString.size())
					.setPayload(byteString)
					.build();

			EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimInit;
			EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
					.setMsg(msgType)
					.setCmdPayload(payloadBuf)
					.build();

			provisionManager.getEspDevice().sendDataToCustomEndPoint("rmaker_claim", payload.toByteArray(), new ResponseListener() {
				@Override
				public void onSuccess(byte[] returnData) {

					Log.e("Send Claim Init Req", "Successfully sent claiming init command");
					getCSRFromDevice(returnData, result);
				}

				@Override
				public void onFailure(Exception e) {
					result.error(new Throwable("Claim Init Failure" + e.getMessage()));
				}
			});
		}

		private void getCSRFromDevice(byte[] responseData, FlutterESP.Result<String> result) {

			try {
				EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.parseFrom(responseData);
				EspRmakerClaim.RespPayload response = payload.getRespPayload();

				if (response.getStatus() == EspRmakerClaim.RMakerClaimStatus.Success) {

					String data = response.getBuf().getPayload().toStringUtf8();
					int offset = response.getBuf().getOffset();
					int totalLen = response.getBuf().getTotalLen();
					Log.d("CSR", "Offset : " + offset + " and total length : " + totalLen);

					if (offset == 0) {
						dataCount = data.length();
						csrData = new StringBuilder();
					}
					csrData.append(data);
					Log.d("Get CSR", "Received CSR Length till now : " + csrData.length());
					Log.d("Get CSR", "dataCount : " + dataCount);

					if (csrData.length() >= totalLen) {
						// Send CSR to Flutter
						result.success(csrData.toString());
					} else {
						requestCSRData(result);
					}
				} else {
					result.error(new Throwable("Get CSR from device Failure Response Status : " + response.getStatus()));
				}

			} catch (InvalidProtocolBufferException e) {
				result.error(new Throwable("Get CSR from device Failure" + e.getMessage()));
			}
		}

		private void requestCSRData(FlutterESP.Result<String> result) {
			EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
					.build();

			EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimInit;
			EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
					.setMsg(msgType)
					.setCmdPayload(payloadBuf)
					.build();

			provisionManager.getEspDevice().sendDataToCustomEndPoint("rmaker_claim", payload.toByteArray(), new ResponseListener() {

				@Override
				public void onSuccess(byte[] returnData) {
					Log.d("Request CSR", "Requesting again!!!!");
					getCSRFromDevice(returnData, result);
				}

				@Override
				public void onFailure(Exception e) {
					result.error(new Throwable("Get CSR from device Failure : " + e.getMessage()));
				}
			});
		}

		@Override
		public void sendCertificate(@NonNull Long offset, @NonNull String certificate, FlutterESP.Result<String> result) {
			Log.d("Send Certificate", "Send certificate to device, offset : " + offset);
			String data = "";

			try {
				int totalLen = certificate.length();
				int len = (int) (offset + dataCount);

				Log.d("Send Certificate", "Length : " + len + " and total len : " + totalLen);

				if (len > totalLen) {
					Log.d("Send Certificate", "Actual end index : " + totalLen);
					data = certificate.substring(Math.toIntExact(offset), totalLen);
				} else {
					Log.d("Send Certificate", "Actual end index : " + len);
					data = certificate.substring(Math.toIntExact(offset), len);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			ByteString byteString = ByteString.copyFromUtf8(data);
			EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
					.setOffset(Math.toIntExact(offset))
					.setTotalLen(certificate.length())
					.setPayload(byteString)
					.build();

			EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimVerify;
			EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
					.setMsg(msgType)
					.setCmdPayload(payloadBuf)
					.build();

			provisionManager.getEspDevice().sendDataToCustomEndPoint("rmaker_claim", payload.toByteArray(), new ResponseListener() {

				@Override
				public void onSuccess(byte[] returnData) {

					if ((offset + dataCount) >= certificate.length()) {

						Log.e("Send Certificate", "Certificate Sent to device successfully.");
						ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

						if (deviceCaps.contains("wifi_scan")) {
							result.success("wifi_scan");
						} else {
							result.success("wifi_config");
						}

					} else {
						sendCertificate(offset + dataCount, certificate, result);
					}
				}

				@Override
				public void onFailure(Exception e) {
					Log.e("Send Certificate", "Send Certificate Error : " + e.getMessage());
					e.printStackTrace();
					result.error(new Throwable("Send Certificate Failure" + e.getMessage()));
				}
			});
		}

		//-----------------------------------------------Associating--------------------------------------------------------
		@Override
		public void associate(@NonNull String user_id, FlutterESP.Result<Map<String, String>> result) {
			Log.d("Associating", "Starting Node Association");
			final String secretKey = UUID.randomUUID().toString();

			EspRainmakerUserMapping.CmdSetUserMapping deviceSecretRequest = EspRainmakerUserMapping.CmdSetUserMapping.newBuilder()
					.setUserID(user_id)
					.setSecretKey(secretKey)
					.build();
			EspRainmakerUserMapping.RMakerConfigMsgType msgType = EspRainmakerUserMapping.RMakerConfigMsgType.TypeCmdSetUserMapping;
			EspRainmakerUserMapping.RMakerConfigPayload payload = EspRainmakerUserMapping.RMakerConfigPayload.newBuilder()
					.setMsg(msgType)
					.setCmdSetUserMapping(deviceSecretRequest)
					.build();

			provisionManager.getEspDevice().sendDataToCustomEndPoint("cloud_user_assoc", payload.toByteArray(), new ResponseListener() {
				@Override
				public void onSuccess(byte[] returnData) {
					Log.d("Associate", "Successfully sent user_id and secret key");
					processDetails(returnData, secretKey, result);
				}

				@Override
				public void onFailure(Exception e) {
					Log.e("Associate", "Send config data Error : " + e.getMessage());
					result.error(new Throwable("Associate Device Send Data Error" + e.getMessage()));
					e.printStackTrace();
				}
			});
		}

		private void processDetails(byte[] responseData, String secretKey, FlutterESP.Result<Map<String, String>> result) {
			try {
				EspRainmakerUserMapping.RMakerConfigPayload payload = EspRainmakerUserMapping.RMakerConfigPayload.parseFrom(responseData);
				EspRainmakerUserMapping.RespSetUserMapping response = payload.getRespSetUserMapping();

				if (response.getStatus() == EspRainmakerUserMapping.RMakerConfigStatus.Success) {
					String receivedNodeId = response.getNodeId();
					HashMap<String, String> data = new HashMap<>();
					data.put("node_id", receivedNodeId);
					data.put("secret_key", secretKey);
					result.success(data);
					Log.d("Associating", "Received Node id : " + receivedNodeId);
				}

			} catch (InvalidProtocolBufferException e) {
				Log.d("Node Id", "Receive Node Id Failure : " + e.getMessage());
				result.error(new Throwable("Receive Node Id Failure : " + e.getMessage()));
				e.printStackTrace();
			}
		}
		//------------------------------------------------------------------------------------------------------------------

		@Override
		public void scanWifiDevices(FlutterESP.Result<List<String>> result) {
			List<String> devices = new ArrayList<>();
			provisionManager.getEspDevice().scanNetworks(new WiFiScanListener() {
				@Override
				public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
					Log.d("Wifi", "Wifi scan complete");
					for (WiFiAccessPoint wifi : wifiList) {
						devices.add(wifi.getWifiName());
					}
					result.success(devices);
				}

				@Override
				public void onWiFiScanFailed(Exception e) {
					Log.d("wifi", "Wifi Scan error " + e.getMessage());
					result.error(new Throwable("Wifi Scan error " + e.getMessage()));
				}
			});
		}

		@Override
		public void provision(@NonNull String ssid, @NonNull String password, FlutterESP.Result<String> result) {
			Log.d("Provision","Starting Provisioning with SSID : " + ssid);
			provisionManager.getEspDevice().provision(ssid, password, new ProvisionListener() {
				@Override
				public void createSessionFailed(Exception e) {
					Log.d("Provision","Create Session Failed : " + e.getMessage());
					result.error(new Throwable("Create Session Failed : " + e.getMessage()));
				}

				@Override
				public void wifiConfigSent() {
					Log.d("Provision","Wifi Config Sent.");
				}

				@Override
				public void wifiConfigFailed(Exception e) {
					Log.d("Provision","Wifi Config Failed : " + e.getMessage());
					result.error(new Throwable("Wifi Config Failed : " + e.getMessage()));
				}

				@Override
				public void wifiConfigApplied() {
					Log.d("Provision","Wifi Config Applied.");
				}

				@Override
				public void wifiConfigApplyFailed(Exception e) {
					Log.d("Provision","Wifi Config Apply Failed : " + e.getMessage());
					result.error(new Throwable("Wifi Config Apply Failed : " + e.getMessage()));
				}

				@Override
				public void provisioningFailedFromDevice(ESPConstants.ProvisionFailureReason failureReason) {
					Log.d("Provision","Provisioning failed from Device : " + failureReason.toString());
					result.error(new Throwable("Provisioning failed from Device : " + failureReason));
				}

				@Override
				public void deviceProvisioningSuccess() {
					EventBus.getDefault().unregister(this);
					result.success("Provision Success");
				}

				@Override
				public void onProvisioningFailed(Exception e) {
					Log.d("Provision","Provisioning Failed : " + e.getMessage());
					result.error(new Throwable("Provisioning Failed : " + e.getMessage()));
				}
			});
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onEvent(DeviceConnectionEvent event) {
		Log.d("BluetoothConnect", "ON Device Prov Event RECEIVED : " + event.getEventType());

		switch (event.getEventType()) {

			case ESPConstants.EVENT_DEVICE_CONNECTED:
				Log.e("BluetoothConnect", "Device Connected Event Received");
				provisionManager.getEspDevice().setProofOfPossession("abcd1234");
				checkDeviceCapabilities();
				break;

			case ESPConstants.EVENT_DEVICE_DISCONNECTED:
				bleConnectResult.error(new Throwable("Device Disconnected"));
				break;

			case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
				bleConnectResult.error(new Throwable("Device Connection Failed" + event.getEventType()));
				break;
		}
	}

	// Checking Device capabilities for claim
	private void checkDeviceCapabilities() {
		String versionInfo = provisionManager.getEspDevice().getVersionInfo();
		ArrayList<String> rmakerCaps = new ArrayList<>();
		ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

		try {
			JSONObject jsonObject = new JSONObject(versionInfo);
			JSONObject rmakerInfo = jsonObject.optJSONObject("rmaker");

			if (rmakerInfo != null) {

				JSONArray rmakerCapabilities = rmakerInfo.optJSONArray("cap");
				if (rmakerCapabilities != null) {
					for (int i = 0; i < rmakerCapabilities.length(); i++) {
						String cap = rmakerCapabilities.getString(i);
						rmakerCaps.add(cap);
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
			Log.d("Version Info", "Version Info JSON not available.");
		}
		if (rmakerCaps.size() > 0 && rmakerCaps.contains("claim")) {
			bleConnectResult.success("claim");

		} else if (deviceCaps != null && deviceCaps.contains("wifi_scan")) {
			bleConnectResult.success("wifi_scan");

		} else {
			bleConnectResult.success("Connection Done. Wifi Config");
		}
	}

	@Override
	public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
		super.configureFlutterEngine(flutterEngine);
		EventBus.getDefault().register(this);
		FlutterESP.EspFlutterProvision.setup(flutterEngine.getDartExecutor().getBinaryMessenger(), new EspAPI());
	}
}
