import 'package:esp_rainmaker_local_control/esp_rainmaker_local_control.dart';
import 'package:flutter/material.dart';

class localControl extends StatefulWidget {
  String accessToken;
  String nodeID;
  localControl({super.key, required this.accessToken, required this.nodeID});

  @override
  State<localControl> createState() => _localControlState();
}

class _localControlState extends State<localControl> {
  late LocalControl control;

  @override
  void initState() {
    control = LocalControl(widget.nodeID);
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Local Control"),
      ),
      body: Scaffold(
        body: Center(
          child: ElevatedButton(onPressed: ()async{
            dynamic data = await control.getParamsValues(true);
            debugPrint(data.toString());
            data["RGB-LED"]["Power"] = !data["RGB-LED"]["Power"];
            await control.updateParamValue(data);
            data = await control.getParamsValues(true);
            debugPrint(data.toString());
          }, child: const Text("Get Data")),
        ),
      ),
    );
  }
}