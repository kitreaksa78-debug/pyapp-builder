import threading
import asyncio
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.clock import Clock
from kivy.core.window import Window

# បណ្ណាល័យ Telegram
from telegram.ext import ApplicationBuilder, MessageHandler, filters

# កំណត់ពណ៌ផ្ទៃខាងក្រោយឱ្យងងឹតបន្តិចសម្រាប់ App
Window.clearcolor = (0.1, 0.1, 0.1, 1)

class TelegramControlApp(App):
    def build(self):
        self.title = "Telegram Bot Control V2"
        self.is_running = False
        self.bot_application = None
        self.bot_thread = None
        self.auto_reply_text = "សូមស្វាគមន៍! ខ្ញុំគឺជា Bot ស្វ័យប្រវត្តិ។"

        # បង្កើត Layout សម្រាប់ UI
        layout = BoxLayout(orientation='vertical', padding=20, spacing=15)

        # ចំណងជើង
        title_label = Label(text="[b]Telegram Bot Control V2[/b]", markup=True, font_size='20sp', size_hint_y=None, height=50)
        layout.add_widget(title_label)

        # ប្រអប់ដាក់ Bot Token
        layout.add_widget(Label(text="Bot Token (ពី @BotFather):", size_hint_y=None, height=30))
        self.token_input = TextInput(hint_text="ឧទាហរណ៍: 123456:ABC-DEF1234ghIkl-zyx", multiline=False, size_hint_y=None, height=50)
        layout.add_widget(self.token_input)

        # ប្រអប់ដាក់ Auto Reply
        layout.add_widget(Label(text="សារឆ្លើយតបស្វ័យប្រវត្តិ (Auto Reply):", size_hint_y=None, height=30))
        self.reply_input = TextInput(text=self.auto_reply_text, multiline=False, size_hint_y=None, height=50)
        self.reply_input.bind(text=self.update_auto_reply)
        layout.add_widget(self.reply_input)

        # ប៊ូតុង Start / Stop
        btn_layout = BoxLayout(size_hint_y=None, height=60, spacing=10)
        self.btn_toggle = Button(text="ចាប់ផ្តើម (Start Bot)", background_color=(0, 0.7, 0, 1), font_size='18sp')
        self.btn_toggle.bind(on_press=self.toggle_bot)
        btn_layout.add_widget(self.btn_toggle)
        layout.add_widget(btn_layout)

        # ផ្ទាំងបង្ហាញស្ថានភាព (Status)
        self.status_label = Label(text="ស្ថានភាព: បិទ (Stopped)", color=(1, 0.3, 0.3, 1), size_hint_y=None, height=50)
        layout.add_widget(self.status_label)

        # ចន្លោះទំនេរខាងក្រោម
        layout.add_widget(Label())

        return layout

    def update_auto_reply(self, instance, value):
        self.auto_reply_text = value

    def toggle_bot(self, instance):
        if not self.is_running:
            token = self.token_input.text.strip()
            if not token or ":" not in token:
                self.update_status("បរាជ័យ: សូមបញ្ចូល Token ឱ្យបានត្រឹមត្រូវ", (1, 0, 0, 1))
                return
            
            self.is_running = True
            self.btn_toggle.text = "បញ្ឈប់ (Stop Bot)"
            self.btn_toggle.background_color = (1, 0, 0, 1)
            self.update_status("កំពុងដំណើរការ...", (1, 1, 0, 1))
            
            # ចាប់ផ្តើម Bot នៅក្នុង Thread ដាច់ដោយឡែក
            self.bot_thread = threading.Thread(target=self.run_bot_thread, args=(token,), daemon=True)
            self.bot_thread.start()
        else:
            # បញ្ឈប់ Bot (សម្រាប់កម្មវិធីសាមញ្ញ យើងគ្រាន់តែកំណត់ Flag និងបិទ App ជាបណ្តោះអាសន្ន)
            self.is_running = False
            if self.bot_application:
                # បញ្ឈប់ Bot ឱ្យបានត្រឹមត្រូវ
                asyncio.run_coroutine_threadsafe(self.bot_application.stop(), self.loop)
            
            self.btn_toggle.text = "ចាប់ផ្តើម (Start Bot)"
            self.btn_toggle.background_color = (0, 0.7, 0, 1)
            self.update_status("ស្ថានភាព: បានបញ្ឈប់", (1, 0.3, 0.3, 1))

    def run_bot_thread(self, token):
        # បង្កើត Event Loop ថ្មីសម្រាប់ Thread នេះ
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        async def handle_message(update, context):
            # មុខងារនេះដំណើរការពេលមានគេផ្ញើសារមក Bot
            if self.auto_reply_text:
                await update.message.reply_text(self.auto_reply_text)

        try:
            # រៀបចំ Application
            self.bot_application = ApplicationBuilder().token(token).build()
            self.bot_application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
            
            # ធ្វើបច្ចុប្បន្នភាព UI ថាដំណើរការ
            Clock.schedule_once(lambda dt: self.update_status("ស្ថានភាព: កំពុងដំណើរការ (Running)", (0, 1, 0, 1)), 0)

            # ចាប់ផ្តើម Bot
            self.loop.run_until_complete(self.bot_application.initialize())
            self.loop.run_until_complete(self.bot_application.start())
            self.loop.run_until_complete(self.bot_application.updater.start_polling())
            
            # រក្សា Loop ឱ្យដំណើរការ
            self.loop.run_forever()
            
        except Exception as e:
            # បើមានបញ្ហា (ដូចជា Token ខុស)
            error_msg = f"កំហុស: {str(e)}"
            Clock.schedule_once(lambda dt: self.update_status(error_msg, (1, 0, 0, 1)), 0)
            Clock.schedule_once(lambda dt: self.reset_ui(), 0)

    def update_status(self, text, color):
        self.status_label.text = text
        self.status_label.color = color

    def reset_ui(self):
        self.is_running = False
        self.btn_toggle.text = "ចាប់ផ្តើម (Start Bot)"
        self.btn_toggle.background_color = (0, 0.7, 0, 1)

if __name__ == '__main__':
    TelegramControlApp().run()
