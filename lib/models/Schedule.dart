
class Schedule {
  final String id;
  final String name;
  final bool isEnabled;
  final Map<String,Map<String,dynamic>> actions;
  final List<Map<String, int>> triggers;

  Schedule({
    required this.id,
    required this.name,
    required this.isEnabled,
    required this.actions,
    required this.triggers,
  });

  factory Schedule.fromJSON(Map<String, dynamic> json) {
    return Schedule(
        id: json["id"],
        name: json["name"],
        isEnabled: json["enabled"],
        actions: json["action"],
        triggers: json["triggers"]);
  }

  Map<String, dynamic> toJSON() {
    return {"id": id, "name": name, "enabled": isEnabled, "action": actions, "triggers": triggers};
  }
}
