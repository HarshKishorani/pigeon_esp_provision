import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'package:pigeon_esp/models/Schedule.dart';
import 'package:weekday_selector/weekday_selector.dart';

class SetSchedule extends StatefulWidget {
  String accessToken;
  String nodeID;
  SetSchedule({super.key, required this.accessToken, required this.nodeID});

  @override
  State<SetSchedule> createState() => _SetScheduleState();
}

class _SetScheduleState extends State<SetSchedule> {
  TimeOfDay result = TimeOfDay.now();
  final values = List.filled(7, false);
  late int m, d;
  TextEditingController sceneText = TextEditingController();
  TextEditingController modeText = TextEditingController();
  int speed = 50;
  int size = 1;
  Map<String, dynamic> params = {};

  getNodeParams() async {
    print("----------------------Getting Params------------------------");
    final queryParameters = {'node_id': widget.nodeID};
    http.Response response = await http.get(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        headers: {HttpHeaders.authorizationHeader: widget.accessToken});
    print("Get Params : ${response.statusCode} : ${response.body}}");
    params = jsonDecode(response.body);
  }

  updateParams(Schedule schedule) async {
    print("----------------------Updating------------------------");
    List schedules = params["Schedule"]["Schedules"] as List;
    schedules.add(schedule.toJSON());
    final queryParameters = {'node_id': widget.nodeID};
    http.Response response = await http.put(
        Uri.https("api.rainmaker.espressif.com", "/v1/user/nodes/params", queryParameters),
        body: jsonEncode(params),
        headers: {HttpHeaders.authorizationHeader: widget.accessToken});
    print("Update Status : ${response.statusCode} : ${response.body}}");
    getNodeParams();
  }

  //! Add this in params["Schedule"]["Schedules"] = [] i.e is a List
  //* {

  //+ Devices and their actions in actions
  /*
        action : {
          DEVICE_1 : {},
          DEVICE_2 : {}
        }
        */

  //*   "action": {
  //*     "Lights": {
  //*       "scene_size": 0,
  //*       "scene_speed": 100,
  //*       "scene_text": "ff000000ff00",
  //*       "scenes_mode": "1"
  //*     }
  //*   },

  //+ Triggers : Time and days
  // m = Schedule_minutes + (Schedule_hours * 60)
  // d = //     StringBuilder days = new StringBuilder("01111111");
  //     String daysStr = days.toString();
  //     int daysValue = Integer.parseInt(daysStr, 2);
  //     System.out.println(daysValue);
  //*   "triggers": [
  //*     {"d": 127, "m": 762}
  //*   ]

  //*   "enabled": true,
  //*   "id": "3NZN",
  //*   "name": "Test",
  //* }

  @override
  void initState() {
    getNodeParams();
    super.initState();
  }

  show() async {
    result = await showTimePicker(
          context: context,
          initialTime: result,
          builder: (context, Widget? child) {
            return MediaQuery(
              data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: false),
              child: child!,
            );
          },
        ) ??
        TimeOfDay.now();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Set Schedule"),
      ),
      body: Padding(
        padding: const EdgeInsets.all(12),
        child: SingleChildScrollView(
          child: Column(
            children: [
              TextField(
                controller: sceneText,
                decoration: const InputDecoration(hintText: "Scene Text to send"),
              ),
              const SizedBox(
                height: 15,
              ),
              TextField(
                controller: modeText,
                decoration: const InputDecoration(hintText: "Mode Text to send"),
              ),
              const SizedBox(
                height: 15,
              ),
              ElevatedButton(
                  onPressed: () {
                    show();
                  },
                  child: Text("${result.hour} : ${result.minute}")),
              const SizedBox(
                height: 12,
              ),
              WeekdaySelector(
                shortWeekdays: const ["M", "T", "W", "T", "F", "S", "S"],
                firstDayOfWeek: 0,
                onChanged: (int day) {
                  debugPrint("Selected : $day");
                  setState(() {
                    final index = day % 7;
                    values[index] = !values[index];
                  });
                  debugPrint("Days Changed : $values");
                },
                values: values,
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
              ElevatedButton(
                  onPressed: () {
                    m = result.minute + result.hour * 60;
                    String days = "0";
                    for (int i = 0; i < 7; i++) {
                      if (values[i]) {
                        days += '1';
                      } else {
                        days += '0';
                      }
                    }
                    d = int.parse(days, radix: 2);

                    Schedule schedule =
                        Schedule(id: "id", name: "Test Scene", isEnabled: true, actions: {
                      "Lights": {
                        "scene_size": size,
                        "scene_speed": speed,
                        "scene_text": sceneText.text,
                        "scenes_mode": modeText.text
                      }
                    }, triggers: [
                      {"m": m, "d": d}
                    ]);
                    updateParams(schedule);
                  },
                  child: const Text("Submit")),
            ],
          ),
        ),
      ),
    );
  }
}
