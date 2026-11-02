import sys

# យើងដាក់ try-except ដើម្បីចាប់ Error បើសិនជាវារកបណ្ណាល័យមិនឃើញ
try:
    import threading
    import asyncio
    from kivy.app import App
    from kivy.uix.boxlayout import BoxLayout
    from kivy.uix.button import Button
    from kivy.uix.label import Label
    from kivy.uix.textinput import TextInput
    from kivy.clock import Clock
    from kivy.core.window import Window
    from kivy.uix.scrollview import ScrollView

    # ព្យាយាម Import Telegram (បើអត់មានវានឹង Error ត្រង់នេះ)
    from telegram.ext import ApplicationBuilder, MessageHandler, filters

    Window.clearcolor = (0.1, 0.1, 0.1, 1)

    class TelegramControlApp(App):
        def build(self):
            self.title = "Telegram Bot Control V2"
            self.is_running = False
            self.bot_application = None
            self.bot_thread = None
            self.auto_reply_text = "សូមស្វាគមន៍! ខ្ញុំគឺជា Bot ស្វ័យប្រវត្តិ។"

            layout = BoxLayout(orientation='vertical', padding=20, spacing=15)
            title_label = Label(text="[b]Telegram Bot V2[/b]", markup=True, font_size='20sp', size_hint_y=None, height=50)
            layout.add_widget(title_label)

            layout.add_widget(Label(text="Bot Token:", size_hint_y=None, height=30))
            self.token_input = TextInput(hint_text="បញ្ចូល Token ទីនេះ", multiline=False, size_hint_y=None, height=50)
            layout.add_widget(self.token_input)

            layout.add_widget(Label(text="Auto Reply:", size_hint_y=None, height=30))
            self.reply_input = TextInput(text=self.auto_reply_text, multiline=False, size_hint_y=None, height=50)
            self.reply_input.bind(text=self.update_auto_reply)
            layout.add_widget(self.reply_input)

            btn_layout = BoxLayout(size_hint_y=None, height=60, spacing=10)
            self.btn_toggle = Button(text="ចាប់ផ្តើម (Start Bot)", background_color=(0, 0.7, 0, 1), font_size='18sp')
            self.btn_toggle.bind(on_press=self.toggle_bot)
            btn_layout.add_widget(self.btn_toggle)
            layout.add_widget(btn_layout)

            self.status_label = Label(text="ស្ថានភាព: បិទ (Stopped)", color=(1, 0.3, 0.3, 1), size_hint_y=None, height=50)
            layout.add_widget(self.status_label)
            layout.add_widget(Label())
            return layout

        def update_auto_reply(self, instance, value):
            self.auto_reply_text = value

        def toggle_bot(self, instance):
            if not self.is_running:
                token = self.token_input.text.strip()
                if not token or ":" not in token:
                    self.update_status("បរាជ័យ: Token មិនត្រឹមត្រូវ", (1, 0, 0, 1))
                    return
                self.is_running = True
                self.btn_toggle.text = "បញ្ឈប់ (Stop Bot)"
                self.btn_toggle.background_color = (1, 0, 0, 1)
                self.update_status("កំពុងដំណើរការ...", (1, 1, 0, 1))
                self.bot_thread = threading.Thread(target=self.run_bot_thread, args=(token,), daemon=True)
                self.bot_thread.start()
            else:
                self.is_running = False
                if self.bot_application:
                    asyncio.run_coroutine_threadsafe(self.bot_application.stop(), self.loop)
                self.reset_ui()

        def run_bot_thread(self, token):
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            async def handle_message(update, context):
                if self.auto_reply_text:
                    await update.message.reply_text(self.auto_reply_text)
            try:
                self.bot_application = ApplicationBuilder().token(token).build()
                self.bot_application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
                Clock.schedule_once(lambda dt: self.update_status("ស្ថានភាព: កំពុងដំណើរការ (Running)", (0, 1, 0, 1)), 0)
                self.loop.run_until_complete(self.bot_application.initialize())
                self.loop.run_until_complete(self.bot_application.start())
                self.loop.run_until_complete(self.bot_application.updater.start_polling())
                self.loop.run_forever()
            except Exception as e:
                Clock.schedule_once(lambda dt: self.update_status(f"កំហុស: {str(e)}", (1, 0, 0, 1)), 0)
                Clock.schedule_once(lambda dt: self.reset_ui(), 0)

        def update_status(self, text, color):
            self.status_label.text = text
            self.status_label.color = color

        def reset_ui(self):
            self.is_running = False
            self.btn_toggle.text = "ចាប់ផ្តើម (Start Bot)"
            self.btn_toggle.background_color = (0, 0.7, 0, 1)
            self.update_status("ស្ថានភាព: បានបញ្ឈប់", (1, 0.3, 0.3, 1))

    if __name__ == '__main__':
        TelegramControlApp().run()

except Exception as e:
    # ប្រសិនបើ Error (ដូចជារក Telegram មិនឃើញ) វានឹងបង្ហាញផ្ទាំងពណ៌ក្រហមនេះជំនួស
    from kivy.app import App
    from kivy.uix.label import Label
    from kivy.uix.scrollview import ScrollView
    
    class ErrorApp(App):
        def build(self):
            scroll = ScrollView()
            lbl = Label(text=f"មានបញ្ហាកើតឡើង:\n\n{str(e)}\n\n(បណ្ណាល័យខ្វះ ឬកូដខុស)", color=(1,0,0,1), halign="center", valign="middle", size_hint_y=None)
            lbl.bind(width=lambda *x: lbl.setter('text_size')(lbl, (lbl.width, None)))
            lbl.bind(texture_size=lbl.setter('size'))
            scroll.add_widget(lbl)
            return scroll

    if __name__ == '__main__':
        ErrorApp().run()
