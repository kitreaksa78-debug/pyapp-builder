# Telegram Bot Control — Kivy Android App
# Paste this entire file into PyApp Builder's main.py field.
# Backend API:
# GET  /status
# GET  /settings
# PUT  /settings
# POST /bot/start
# POST /bot/stop

import json
import threading
import urllib.request
import urllib.error

from kivy.app import App
from kivy.clock import Clock
from kivy.metrics import dp, sp
from kivy.utils import get_color_from_hex
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.scrollview import ScrollView
from kivy.uix.switch import Switch
from kivy.uix.widget import Widget


BG = "#0B1020"
PANEL = "#141C2F"
PANEL_2 = "#1B2740"
TEXT = "#F4F7FF"
MUTED = "#91A0BA"
BLUE = "#4F8CFF"
GREEN = "#22C78A"
RED = "#F06472"


def rgba(value):
    return get_color_from_hex(value)


class RoundedPanel(BoxLayout):

    def __init__(self, bg=PANEL, radius=18, **kwargs):
        super().__init__(**kwargs)

        with self.canvas.before:
            self._color = Color(*rgba(bg))
            self._shape = RoundedRectangle(
                pos=self.pos,
                size=self.size,
                radius=[dp(radius)] * 4
            )

        self.bind(pos=self._sync, size=self._sync)

    def _sync(self, *_):
        self._shape.pos = self.pos
        self._shape.size = self.size


class AppLabel(Label):

    def __init__(self, text="", color=TEXT,
                 size=14, bold=False, **kwargs):

        super().__init__(
            text=text,
            color=rgba(color),
            font_size=sp(size),
            bold=bold,
            **kwargs
        )


class ActionButton(Button):

    def __init__(self, text, bg=BLUE, **kwargs):

        super().__init__(
            text=text,
            color=rgba(TEXT),
            bold=True,
            font_size=sp(14),
            background_normal="",
            background_down="",
            background_color=(0, 0, 0, 0),
            size_hint_y=None,
            height=dp(48),
            **kwargs
        )

        with self.canvas.before:
            self._btn_color = Color(*rgba(bg))
            self._btn_shape = RoundedRectangle(
                pos=self.pos,
                size=self.size,
                radius=[dp(13)] * 4
            )

        self.bind(pos=self._update_shape,
                  size=self._update_shape)

    def _update_shape(self, *_):
        self._btn_shape.pos = self.pos
        self._btn_shape.size = self.size


class Field(TextInput):

    def __init__(self, text="", hint_text="",
                 password=False, multiline=False,
                 height=48, **kwargs):

        super().__init__(
            text=text,
            hint_text=hint_text,
            password=password,
            multiline=multiline,
            size_hint_y=None,
            height=dp(height),
            padding=[dp(13), dp(12), dp(13), dp(10)],
            font_size=sp(14),
            foreground_color=rgba(TEXT),
            hint_text_color=rgba(MUTED),
            cursor_color=rgba(BLUE),
            background_normal="",
            background_active="",
            background_color=rgba(PANEL_2),
            **kwargs
        )


class SectionTitle(AppLabel):

    def __init__(self, text, **kwargs):

        super().__init__(
            text=text,
            size=16,
            bold=True,
            size_hint_y=None,
            height=dp(34),
            halign="left",
            valign="middle",
            **kwargs
        )

        self.bind(
            size=lambda obj, val:
            setattr(obj, "text_size", val)
        )


class TelegramControlApp(App):

    title = "Telegram Bot Control"

    def build(self):

        Window.clearcolor = rgba(BG)

        self.busy = False

        outer = BoxLayout(
            orientation="vertical",
            padding=[dp(14), dp(10)],
            spacing=dp(10)
        )

        # HEADER

        header = BoxLayout(
            orientation="horizontal",
            size_hint_y=None,
            height=dp(64),
            spacing=dp(10)
        )

        logo = RoundedPanel(
            bg="#203B68",
            size_hint=(None, None),
            size=(dp(52), dp(52)),
            radius=16
        )

        logo.add_widget(
            AppLabel(text="✈", size=27, bold=True)
        )

        header.add_widget(logo)

        brand = BoxLayout(
            orientation="vertical",
            spacing=0
        )

        brand.add_widget(
            AppLabel(
                text="TELEGRAM CONTROL",
                size=17,
                bold=True,
                halign="left",
                valign="bottom"
            )
        )

        brand.add_widget(
            AppLabel(
                text="BOT MANAGEMENT CONSOLE",
                color=MUTED,
                size=10,
                halign="left",
                valign="top"
            )
        )

        header.add_widget(brand)
        header.add_widget(Widget())

        outer.add_widget(header)

        # SCROLL CONTENT

        scroll = ScrollView(
            do_scroll_x=False,
            bar_width=dp(3)
        )

        self.content = BoxLayout(
            orientation="vertical",
            spacing=dp(12),
            size_hint_y=None,
            padding=[0, 0, 0, dp(16)]
        )

        self.content.bind(
            minimum_height=self.content.setter("height")
        )

        scroll.add_widget(self.content)
        outer.add_widget(scroll)

        # CONNECTION

        self.content.add_widget(
            SectionTitle("CONNECTION")
        )

        conn = RoundedPanel(
            orientation="vertical",
            padding=dp(14),
            spacing=dp(9),
            size_hint_y=None
        )

        conn.bind(
            minimum_height=lambda obj, val:
            setattr(obj, "height", val)
        )

        conn.add_widget(
            AppLabel(
                text="Backend HTTPS URL",
                color=MUTED,
                size=11,
                size_hint_y=None,
                height=dp(20),
                halign="left"
            )
        )

        self.server = Field(
            hint_text="https://your-service.onrender.com"
        )

        conn.add_widget(self.server)

        conn.add_widget(
            AppLabel(
                text="Admin API Key",
                color=MUTED,
                size=11,
                size_hint_y=None,
                height=dp(20),
                halign="left"
            )
        )

        self.api_key = Field(
            hint_text="Paste your private admin key",
            password=True
        )

        conn.add_widget(self.api_key)

        self.status = AppLabel(
            text="●  NOT CONNECTED",
            color=MUTED,
            size=12,
            bold=True,
            size_hint_y=None,
            height=dp(26),
            halign="left"
        )

        conn.add_widget(self.status)

        conn.add_widget(
            ActionButton(
                "CONNECT  /  CHECK STATUS",
                bg=BLUE,
                on_release=self.refresh_status
            )
        )

        self.content.add_widget(conn)

        # BOT CONTROLS

        self.content.add_widget(
            SectionTitle("BOT CONTROLS")
        )

        controls = GridLayout(
            cols=2,
            spacing=dp(9),
            size_hint_y=None,
            row_default_height=dp(54),
            row_force_default=True
        )

        controls.add_widget(
            ActionButton(
                "▶  START BOT",
                bg=GREEN,
                on_release=self.start_bot
            )
        )

        controls.add_widget(
            ActionButton(
                "■  STOP BOT",
                bg=RED,
                on_release=self.stop_bot
            )
        )

        self.content.add_widget(controls)

        # AUTO REPLY

        self.content.add_widget(
            SectionTitle("AUTO REPLY")
        )

        auto = RoundedPanel(
            orientation="horizontal",
            padding=[dp(14), dp(8)],
            spacing=dp(8),
            size_hint_y=None,
            height=dp(64)
        )

        auto_text = BoxLayout(
            orientation="vertical",
            spacing=dp(1)
        )

        auto_text.add_widget(
            AppLabel(
                text="Automatic replies",
                size=14,
                bold=True,
                halign="left",
                valign="bottom"
            )
        )

        auto_text.add_widget(
            AppLabel(
                text="Enable keyword and default responses",
                size=10,
                color=MUTED,
                halign="left",
                valign="top"
            )
        )

        auto.add_widget(auto_text)

        self.auto_switch = Switch(
            active=True,
            size_hint=(None, None),
            size=(dp(55), dp(42))
        )

        auto.add_widget(self.auto_switch)

        self.content.add_widget(auto)

        # WELCOME MESSAGE

        self.content.add_widget(
            SectionTitle("WELCOME MESSAGE")
        )

        self.welcome = Field(
            text="សួស្តី! សូមស្វាគមន៍មកកាន់ Bot របស់យើង។",
            hint_text="Message sent after /start",
            multiline=True,
            height=88
        )

        self.content.add_widget(self.welcome)

        # KEYWORD REPLIES

        self.content.add_widget(
            SectionTitle("KEYWORD REPLIES")
        )

        self.content.add_widget(
            AppLabel(
                text='កំណត់ជា JSON list។ match អាចជា "contains" ឬ "exact"។',
                color=MUTED,
                size=11,
                size_hint_y=None,
                height=dp(28),
                halign="left"
            )
        )

        self.rules = Field(
            text='[\n'
                 '  {"keyword":"សួស្តី",'
                 '"reply":"ជំរាបសួរ! តើខ្ញុំអាចជួយអ្វីបាន?",'
                 '"match":"contains"},\n'
                 '  {"keyword":"តម្លៃ",'
                 '"reply":"សូមប្រាប់ឈ្មោះផលិតផលដែលអ្នកចង់ដឹង។",'
                 '"match":"contains"}\n'
                 ']',
            multiline=True,
            height=190
        )

        self.content.add_widget(self.rules)

        # DEFAULT REPLY

        self.content.add_widget(
            SectionTitle("DEFAULT REPLY")
        )

        self.default_reply = Field(
            text="សូមអរគុណសម្រាប់សាររបស់អ្នក។ តើអ្នកចង់សួរអំពីអ្វី?",
            hint_text="Sent when no keyword matches",
            multiline=True,
            height=85
        )

        self.content.add_widget(self.default_reply)

        # SAVE SETTINGS

        self.content.add_widget(
            ActionButton(
                "SAVE AUTO REPLY SETTINGS",
                bg=BLUE,
                on_release=self.save_settings
            )
        )

        self.content.add_widget(
            ActionButton(
                "LOAD SETTINGS FROM SERVER",
                bg=PANEL_2,
                on_release=self.load_settings
            )
        )

        footer = AppLabel(
            text="SECURITY  •  Keep your Admin API Key private\n"
                 "Telegram Bot Token stays on your backend server.",
            color=MUTED,
            size=10,
            halign="center",
            size_hint_y=None,
            height=dp(42)
        )

        self.content.add_widget(footer)

        return outer

    # API CONNECTION

    def api(self, method, path, payload=None):

        base = self.server.text.strip().rstrip("/")
        key = self.api_key.text.strip()

        if not base.startswith("https://"):
            raise ValueError(
                "Backend URL ត្រូវប្រើ HTTPS"
            )

        if not key:
            raise ValueError(
                "សូមបញ្ចូល Admin API Key"
            )

        body = None

        if payload is not None:
            body = json.dumps(
                payload,
                ensure_ascii=False
            ).encode("utf-8")

        request = urllib.request.Request(
            base + path,
            data=body,
            method=method,
            headers={
                "X-API-Key": key,
                "Content-Type": "application/json"
            }
        )

        try:

            with urllib.request.urlopen(
                request,
                timeout=25
            ) as response:

                result = response.read().decode("utf-8")

                return json.loads(result) if result else {}

        except urllib.error.HTTPError as exc:

            detail = exc.read().decode(
                "utf-8",
                errors="replace"
            )

            raise RuntimeError(
                "HTTP %s — %s" % (exc.code, detail)
            )

        except Exception as exc:

            raise RuntimeError(str(exc))

    # BACKGROUND TASK

    def background_task(self, task, on_success=None):

        if self.busy:
            self.set_status("Please wait…", MUTED)
            return

        self.busy = True

        self.set_status("CONNECTING…", BLUE)

        def worker():

            try:

                result = task()

                Clock.schedule_once(
                    lambda dt:
                    self._finish_success(result, on_success),
                    0
                )

            except Exception as exc:

                message = str(exc)

                Clock.schedule_once(
                    lambda dt:
                    self._finish_error(message),
                    0
                )

        threading.Thread(
            target=worker,
            daemon=True
        ).start()

    def _finish_success(self, result, callback):

        self.busy = False

        if callback:
            callback(result)
        else:
            self.set_status("✓  SUCCESS", GREEN)

    def _finish_error(self, message):

        self.busy = False

        self.set_status(
            "⚠  " + message,
            RED
        )

    def set_status(self, text, color):

        self.status.text = text
        self.status.color = rgba(color)

    # CHECK STATUS

    def refresh_status(self, *_):

        def done(data):

            running = data.get("bot_running", False)
            token = data.get("token_configured", False)
            auto = data.get("auto_reply_enabled", False)

            state = "RUNNING" if running else "STOPPED"

            self.set_status(
                "●  BOT %s  |  TOKEN %s  |  AUTO %s"
                % (
                    state,
                    "OK" if token else "MISSING",
                    "ON" if auto else "OFF"
                ),
                GREEN if running else MUTED
            )

        self.background_task(
            lambda: self.api("GET", "/status"),
            done
        )

    # START BOT

    def start_bot(self, *_):

        def done(_):

            self.set_status(
                "✓  BOT STARTED",
                GREEN
            )

        self.background_task(
            lambda: self.api(
                "POST",
                "/bot/start",
                {}
            ),
            done
        )

    # STOP BOT

    def stop_bot(self, *_):

        def done(_):

            self.set_status(
                "■  BOT STOPPED",
                RED
            )

        self.background_task(
            lambda: self.api(
                "POST",
                "/bot/stop",
                {}
            ),
            done
        )

    # SAVE AUTO REPLY

    def save_settings(self, *_):

        try:

            rules = json.loads(
                self.rules.text or "[]"
            )

            if not isinstance(rules, list):

                raise ValueError(
                    "Keyword replies ត្រូវជា JSON list"
                )

            for item in rules:

                if (
                    not isinstance(item, dict)
                    or not item.get("keyword")
                    or not item.get("reply")
                ):

                    raise ValueError(
                        "រាល់ rule ត្រូវមាន keyword និង reply"
                    )

                if item.get(
                    "match",
                    "contains"
                ) not in ("contains", "exact"):

                    raise ValueError(
                        'match ត្រូវជា "contains" ឬ "exact"'
                    )

            payload = {
                "auto_reply_enabled": bool(
                    self.auto_switch.active
                ),
                "welcome_message": self.welcome.text,
                "default_reply": self.default_reply.text,
                "keyword_replies": rules
            }

        except Exception as exc:

            self.set_status(
                "⚠  " + str(exc),
                RED
            )

            return

        def done(_):

            self.set_status(
                "✓  SETTINGS SAVED",
                GREEN
            )

        self.background_task(
            lambda: self.api(
                "PUT",
                "/settings",
                payload
            ),
            done
        )

    # LOAD SETTINGS

    def load_settings(self, *_):

        def done(data):

            self.auto_switch.active = data.get(
                "auto_reply_enabled",
                True
            )

            self.welcome.text = data.get(
                "welcome_message",
                ""
            )

            self.default_reply.text = data.get(
                "default_reply",
                ""
            )

            self.rules.text = json.dumps(
                data.get("keyword_replies", []),
                ensure_ascii=False,
                indent=2
            )

            self.set_status(
                "✓  SETTINGS LOADED",
                GREEN
            )

        self.background_task(
            lambda: self.api(
                "GET",
                "/settings"
            ),
            done
        )


if __name__ == "__main__":

    TelegramControlApp().run()
