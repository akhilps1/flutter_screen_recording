import 'dart:async';

import 'package:flutter/services.dart';

import 'flutter_screen_recording_platform_interface.dart';

class MethodChannelFlutterScreenRecording
    extends FlutterScreenRecordingPlatform {
  static const MethodChannel _channel =
      MethodChannel('flutter_screen_recording');

  @override
  Future<bool> startRecordScreen(
    String name, {
    String notificationTitle = "",
    String notificationMessage = "",
  }) async {
    final bool start = await _channel.invokeMethod('startRecordScreen', {
      "name": name,
      "audio": false,
      "title": notificationTitle,
      "message": notificationMessage,
    });
    return start;
  }

  @override
  Future<bool> startRecordScreenAndAudio(
    String name, {
    String notificationTitle = "",
    String notificationMessage = "",
  }) async {
    final bool start = await _channel.invokeMethod(
      'startRecordScreen',
      {
        "name": name,
        "audio": true,
        "title": notificationTitle,
        "message": notificationMessage,
      },
    );
    return start;
  }

  @override
  Future<String> get stopRecordScreen async {
    final String path = await _channel.invokeMethod('stopRecordScreen');
    return path;
  }

  @override
  Future<bool> initIntent() async {
    final bool res = await _channel.invokeMethod('initIntent');

    return res;
  }
}
