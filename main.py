from kivy.app import App
from kivy.uix.label import Label


class MainApp(App):
    def build(self):
        return Label(text="Built on Render")


MainApp().run()
