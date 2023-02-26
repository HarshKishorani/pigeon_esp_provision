import 'package:pigeon/pigeon.dart';

@HostApi()
abstract class EspFlutterProvision{
  //----------------------------Bluetooth Connection-------------------------------------

  //* Step 1 : return list of Bluetooth Devices
  @async
  List<String?> scanDevices(String prefix);

  //* Step 2 : Return Capability of Device claim, wifi_scan, other
  //           Set POP automatically : abcd1234
  @async
  String connectBleDevice(String deviceName);

  //----------------------------------Claiming------------------------------------------

  //* Step 3.1 : Get mac_addr and platform from device for Claiming
  //             Send this mac_addr and platform to cloud and get node_id
  @async
  String startClaim();

  //* Step 3.2 : Send node_id from cloud and get CSR from device
  //             Send this CSR to cloud and get certificate
  @async
  String getCSR(String node_id);

  //* Step 3.3 : Send certificate to cloud and get claiming response from device
  @async
  String sendCertificate(int offset,String certificate);

  //----------------------------Node Association-----------------------------------------

  //* Step 4 : Send your user_id to device and get node_id and secret key
  //           Send this node_id with cloud using package : 
  @async
  Map<String,String>? associate(String user_id);

  //----------------------------Wifi Scan------------------------------------------------

  //* Step 5 : return list of Wifi Devices scanned by the Device
  @async
  List<String?> scanWifiDevices();

  //-------------------------------Provision--------------------------------------------
  
  //* Step 6 : Provision status
  @async
  String provision(String ssid,String password);
}