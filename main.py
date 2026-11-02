__version__ = "1.0.0"

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.core.window import Window

class TestApp(App):
    def build(self):
        Window.clearcolor = (0.05, 0.07, 0.12, 1)

        layout = BoxLayout(
            orientation="vertical",
            padding=30,
            spacing=20
        )

        title = Label(
            text="KIVY TEST SUCCESS!",
            font_size="25sp",
            bold=True
        )

        message = Label(
            text="Your Android App is Working.",
            font_size="18sp"
        )

        button = Button(
            text="CLICK ME",
            font_size="20sp",
            size_hint=(1, 0.3)
        )

        button.bind(
            on_press=lambda instance:
            setattr(message, "text", "BUTTON WORKS!")
        )

        layout.add_widget(title)
        layout.add_widget(message)
        layout.add_widget(button)

        return layout

if __name__ == "__main__":
    TestApp().run()
