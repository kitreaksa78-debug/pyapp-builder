from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label


class MainApp(App):
    title = "My Python App"

    def build(self):
        root = BoxLayout(orientation="vertical", padding=24, spacing=16)
        self.msg = Label(text="Hello from Python!", font_size="26sp")
        btn = Button(text="Tap me", size_hint=(1, 0.3))
        btn.bind(on_release=self.on_tap)
        root.add_widget(self.msg)
        root.add_widget(btn)
        self.taps = 0
        return root

    def on_tap(self, *_):
        self.taps += 1
        self.msg.text = "You tapped %d time(s)" % self.taps


MainApp().run()
