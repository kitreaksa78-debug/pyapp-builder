from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.clock import Clock
from kivy.core.window import Window
import threading
import requests
import time

# កំណត់ពណ៌ផ្ទៃខាងក្រោយឱ្យងងឹតស្អាត
Window.clearcolor = (0.1, 0.1, 0.1, 1)

class TelegramLiteApp(App):
    def build(self):
        self.title = "Telegram Bot Lite"
        self.is_running = False
        self.bot_thread = None

        # បង្កើត Layout សម្រាប់ដាក់ UI
        layout = BoxLayout(orientation='vertical', padding=20, spacing=15)

        # ចំណងជើង
        layout.add_widget(Label(text="[b]Telegram Bot Controller[/b]", markup=True, font_size='22sp', size_hint_y=None, height=50))

        # ប្រអប់ដាក់ Bot Token
        layout.add_widget(Label(text="Bot Token (ពី @BotFather):", size_hint_y=None, height=30))
        self.token_input = TextInput(hint_text="ឧទាហរណ៍: 123456:ABC-DEF...", multiline=False, size_hint_y=None, height=50)
        layout.add_widget(self.token_input)

        # ប្រអប់ដាក់សារឆ្លើយតប
        layout.add_widget(Label(text="សារឆ្លើយតប (Auto Reply):", size_hint_y=None, height=30))
        self.reply_input = TextInput(text="សូមស្វាគមន៍! ខ្ញុំគឺជា Bot ស្វ័យប្រវត្តិ។", multiline=False, size_hint_y=None, height=50)
        layout.add_widget(self.reply_input)

        # ប៊ូតុង Start/Stop
        self.btn_toggle = Button(text="ចាប់ផ្តើម (Start Bot)", background_color=(0, 0.7, 0, 1), font_size='18sp', size_hint_y=None, height=60)
        self.btn_toggle.bind(on_press=self.toggle_bot)
        layout.add_widget(self.btn_toggle)

        # ផ្ទាំងបង្ហាញស្ថានភាព
        self.status_label = Label(text="ស្ថានភាព: បិទ", color=(1, 0.3, 0.3, 1), size_hint_y=None, height=50)
        layout.add_widget(self.status_label)
        
        # ចន្លោះទំនេរខាងក្រោម
        layout.add_widget(Label()) 

        return layout

    def toggle_bot(self, instance):
        if not self.is_running:
            token = self.token_input.text.strip()
            if not token or ":" not in token:
                self.update_status("បរាជ័យ: Token មិនត្រឹមត្រូវ", (1, 0, 0, 1))
                return
            
            self.is_running = True
            self.btn_toggle.text = "បញ្ឈប់ (Stop Bot)"
            self.btn_toggle.background_color = (1, 0, 0, 1)
            self.update_status("ស្ថានភាព: កំពុងដំណើរការ...", (0, 1, 0, 1))
            
            # ចាប់ផ្តើម Bot ក្នុង Thread ដើម្បីកុំឱ្យ UI គាំង
            self.bot_thread = threading.Thread(target=self.run_bot, args=(token,), daemon=True)
            self.bot_thread.start()
        else:
            self.is_running = False
            self.reset_ui()

    def run_bot(self, token):
        offset = 0
        url = f"https://api.telegram.org/bot{token}/getUpdates"
        
        while self.is_running:
            try:
                # ទាញយកសារថ្មីៗពី Telegram (រង់ចាំ 5 វិនាទី)
                response = requests.get(f"{url}?offset={offset}&timeout=5", timeout=10).json()
                
                if response.get("ok"):
                    for update in response["result"]:
                        offset = update["update_id"] + 1
                        
                        # បើមានសារអត្ថបទផ្ញើមក
                        if "message" in update and "text" in update["message"]:
                            chat_id = update["message"]["chat"]["id"]
                            reply_text = self.reply_input.text
                            
                            # ផ្ញើសារតបទៅវិញ
                            requests.post(f"https://api.telegram.org/bot{token}/sendMessage", 
                                          data={"chat_id": chat_id, "text": reply_text})
            except Exception as e:
                # បើអ៊ីនធឺណិតដាច់ ឬ Error សូមរង់ចាំបន្តិច
                time.sleep(2)
            
            time.sleep(1) # កាត់បន្ថយការប្រើប្រាស់ CPU

    def update_status(self, text, color):
        self.status_label.text = text
        self.status_label.color = color

    def reset_ui(self):
        self.is_running = False
        self.btn_toggle.text = "ចាប់ផ្តើម (Start Bot)"
        self.btn_toggle.background_color = (0, 0.7, 0, 1)
        self.update_status("ស្ថានភាព: បានបញ្ឈប់", (1, 0.3, 0.3, 1))

if __name__ == '__main__':
    TelegramLiteApp().run()
