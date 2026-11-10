import 'package:flutter/material.dart';

void main() => runApp(const QaApp());

class QaApp extends StatelessWidget {
  const QaApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        home: Scaffold(
          appBar: AppBar(title: const Text('QA Flutter')),
          body: const Center(
            child: Text('Flutter built by CI', style: TextStyle(fontSize: 20)),
          ),
        ),
      );
}
