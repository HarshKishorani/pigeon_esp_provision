import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

class Scene extends StatefulWidget {
  String accessToken;
  String nodeID;
  Scene({super.key, required this.accessToken, required this.nodeID});
  // Scene({super.key});

  @override
  State<Scene> createState() => _SceneState();
}

class _SceneState extends State<Scene> {
  int mode = 0;
  int size = 0;
  TextEditingController sceneText = TextEditingController();
  int speed = 50;
  Map<String, dynamic> params = {};
  bool fade = false;
  bool breath = false;
  bool flash = false;

  getNodeParams() async {
    print("----------------------Getting Params------------------------");
    final queryParameters = {'node_id': widget.nodeID};
    http.Response response = await http.get(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        headers: {HttpHeaders.authorizationHeader: widget.accessToken});
    params = jsonDecode(response.body);
    print(params["Lights"]);
  }

  updateParams() async {
    print("----------------------Updating------------------------");
    params["Lights"]["scene_text"] = sceneText.text;
    params["Lights"]["scenes_mode"] = mode;
    params["Lights"]["scene_speed"] = speed;
    params["Lights"]["scene_size"] = size;
    final queryParameters = {'node_id': widget.nodeID};
    http.Response response = await http.put(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        body: jsonEncode(params),
        headers: {HttpHeaders.authorizationHeader: widget.accessToken});
    print("Update Status : ${response.statusCode}");
    getNodeParams();
  }

  @override
  void initState() {
    getNodeParams();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              TextField(
                controller: sceneText,
                decoration: const InputDecoration(hintText: "Scene Text to send"),
              ),
              const SizedBox(
                height: 15,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  ElevatedButton(
                      style: fade ? ElevatedButton.styleFrom(backgroundColor: Colors.red) : null,
                      onPressed: () {
                        setState(() {
                          fade = true;
                          breath = false;
                          flash = false;
                          mode = 3;
                        });
                      },
                      child: const Text("Fade")),
                  ElevatedButton(
                      style: breath ? ElevatedButton.styleFrom(backgroundColor: Colors.red) : null,
                      onPressed: () {
                        setState(() {
                          fade = false;
                          breath = true;
                          flash = false;
                          mode = 2;
                        });
                      },
                      child: const Text("Breath")),
                  ElevatedButton(
                      style: flash ? ElevatedButton.styleFrom(backgroundColor: Colors.red) : null,
                      onPressed: () {
                        setState(() {
                          fade = false;
                          breath = false;
                          flash = true;
                          mode = 1;
                        });
                      },
                      child: const Text("Flash")),
                ],
              ),
              const SizedBox(
                height: 8,
              ),
              Row(
                children: [
                  Text("Speed : $speed"),
                  const SizedBox(
                    width: 5,
                  ),
                  Expanded(
                    child: Slider(
                        label: speed.round().toString(),
                        divisions: 100,
                        max: 100,
                        min: 1,
                        value: speed.toDouble(),
                        onChanged: (value) {
                          setState(() {
                            speed = value.round();
                          });
                        }),
                  ),
                ],
              ),
              const SizedBox(
                height: 8,
              ),
              Row(
                children: [
                  Text("Size : $size"),
                  const SizedBox(
                    width: 5,
                  ),
                  Expanded(
                    child: Slider(
                        label: size.round().toString(),
                        divisions: 20,
                        max: 20,
                        min: 0,
                        value: size.toDouble(),
                        onChanged: (value) {
                          setState(() {
                            size = value.round();
                          });
                        }),
                  ),
                ],
              ),
              const SizedBox(
                height: 12,
              ),
              ElevatedButton(onPressed: updateParams, child: const Text("Update")),
            ],
          ),
        ),
      ),
    );
  }
}
