import 'dart:convert';
import 'dart:io';

import 'package:esp_rainmaker_local_control/esp_rainmaker_local_control.dart';
import 'package:flutter/material.dart';
import 'package:pigeon_esp/FlutterESP.dart';
import 'package:http/http.dart' as http;
import 'package:esp_rainmaker/esp_rainmaker.dart';
import 'package:pigeon_esp/LocalControl/local_control.dart';
import 'package:permission_handler/permission_handler.dart';
Future<void> main() async {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      // home: const SetSchedule(),
      home: const MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final EspFlutterProvision _espFlutterProvision = EspFlutterProvision();
  List<String?> devices = [];
  final user = User();
  late LoginSuccessResponse login;
  late String token;
  late String userId;
  Map<String?, String?> associate = {};
  late NodeAssociation nodeAssociation;
  late String reqId;
  Map<String, dynamic> params = {};

  showResult(String result) {
    debugPrint("###########################################################################");
    debugPrint("");
    debugPrint("Result Recieved : $result");
    debugPrint("");
    debugPrint("###########################################################################");
  }

  Future getNodeParams() async {
    final queryParameters = {'node_id': associate['node_id']!};
    http.Response response = await http.get(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        headers: {HttpHeaders.authorizationHeader: token});
    debugPrint("Response Received : ${response.body}");
    params = jsonDecode(response.body);
  }

  updateParams() async {
    print("----------------------Updating------------------------");
    // params["Time"]["TZ"] = "Asia/Kolkata";
    // params["Time"]["TZ-POSIX"] = "IST-5:30";
    final queryParameters = {'node_id': associate['node_id']!};
    http.Response response = await http.put(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        body: jsonEncode(params),
        headers: {HttpHeaders.authorizationHeader: token});
    debugPrint("Update Status : ${response.statusCode}");
    await getNodeParams();
  }

  loginUser() async {
    await Permission.bluetoothScan.request();
    login = await user.login('kishoraniharsh@gmail.com', 'Harshkk@002');
    await user
        .extendSession('kishoraniharsh@gmail.com', login.refreshToken)
        .then((value) => token = value.accessToken);
    await user.getUser(token).then((value) {
      userId = value.id;
    });
    debugPrint("Token : $token");
    debugPrint("User Id : $userId");
  }

  claim() async {
    debugPrint("---------------------------Claim Starting------------------------------------");
    String data = await _espFlutterProvision.startClaim();
    String newData = data.replaceAll(RegExp('{[^A-Za-z0-9:",]}'), '');
    http.Response response = await http.post(
        Uri.https("esp-claiming.rainmaker.espressif.com", "/claim/initiate"),
        body: newData,
        headers: {HttpHeaders.authorizationHeader: token});
    debugPrint("Claiming step 1 result received : ${response.statusCode}");
    String CSR = await _espFlutterProvision.getCSR(response.body);
    response = await http.post(Uri.https("esp-claiming.rainmaker.espressif.com", "/claim/verify"),
        body: CSR, headers: {HttpHeaders.authorizationHeader: token});
    debugPrint("Claiming step 2 result received : ${response.statusCode}");
    String result = await _espFlutterProvision.sendCertificate(0, response.body);
    showResult(result);
  }

  Future<void> refresh() async {
    MappingStatus mp = await nodeAssociation.getMappingStatus(reqId);
    print("Mapping status : ${mp.status.toString()}");

    if (mp.status == MappingRequestStatus.confirmed) {
      //* For Time service if exists
      // await getNodeParams();
      print("-------------------------Navigating---------------------------------");
      Navigator.push(
          context,
          MaterialPageRoute(
              builder: (context) =>
                  localControl(accessToken: token, nodeID: associate['node_id']!)));
    }
  }

  @override
  void initState() {
    loginUser();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
                onPressed: () async {
                  try {
                    devices = await _espFlutterProvision.scanDevices("PROV_");
                    showResult(devices.toString());
                  } catch (e) {
                    showResult(e.toString());
                  }
                },
                child: const Text("Bluetooth Scan")),
            const SizedBox(
              height: 12,
            ),
            ElevatedButton(
                onPressed: () async {
                  String result = await _espFlutterProvision.connectBleDevice("PROV_1234");
                  showResult(result);
                  if (result == "claim") {
                    claim();
                  }
                },
                child: const Text("Bluetooth Connect")),
            const SizedBox(
              height: 12,
            ),
            ElevatedButton(
                onPressed: () async {
                  final result = await _espFlutterProvision.associate(userId);
                  associate = result ?? {};
                  showResult(result.toString());
                },
                child: const Text("Associate")),
            const SizedBox(
              height: 12,
            ),
            ElevatedButton(
                onPressed: () async {
                  final result = await _espFlutterProvision.scanWifiDevices();
                  showResult(result.toString());
                },
                child: const Text("Wifi Scan")),
            const SizedBox(
              height: 12,
            ),
            ElevatedButton(
                onPressed: () async {
                  final result =
                      await _espFlutterProvision.provision("Helium Smart", "Helium@8844");
                  showResult(result.toString());
                  if (associate.isNotEmpty) {
                    nodeAssociation = NodeAssociation(token);
                    reqId = await nodeAssociation.addNodeMapping(
                        associate['node_id']!, associate['secret_key']!);
                    MappingStatus mp = await nodeAssociation.getMappingStatus(reqId);
                    print("User Node Mapping status : ${mp.status.toString()}");
                  }
                },
                child: const Text("Provision")),
            const SizedBox(
              height: 12,
            ),
            ElevatedButton(onPressed: refresh, child: const Text("Refresh")),
            // const SizedBox(
            //   height: 12,
            // ),
            // ElevatedButton(onPressed: updateParams, child: const Text("Update Time Zone")),
          ],
        ),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }
}
