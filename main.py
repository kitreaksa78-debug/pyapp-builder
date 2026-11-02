from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from threading import Thread
import requests

class InternetTest(App):
    def build(self):
        self.layout = BoxLayout(
            orientation="vertical",
            padding=25,
            spacing=20
        )

        self.status = Label(
            text="INTERNET TEST\n\nPress the button",
            font_size="20sp"
        )

        btn = Button(
            text="TEST INTERNET",
            font_size="20sp"
        )
        btn.bind(on_press=self.test_internet)

        self.layout.add_widget(self.status)
        self.layout.add_widget(btn)
        return self.layout

    def test_internet(self, instance):
        self.status.text = "Connecting..."
        Thread(target=self.check).start()

    def check(self):
        try:
            r = requests.get(
                "https://httpbin.org/get",
                timeout=15
            )
            result = "INTERNET WORKS!\nHTTP " + str(r.status_code)
        except Exception as e:
            result = "CONNECTION FAILED:\n" + str(e)

        from kivy.clock import Clock
        Clock.schedule_once(
            lambda dt: setattr(self.status, "text", result)
        )

if __name__ == "__main__":
    InternetTest().run()
