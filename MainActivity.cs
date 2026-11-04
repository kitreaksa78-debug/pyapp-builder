using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Webkit;

namespace PyApp
{
    /// <summary>
    /// App Android សាងសង់ដោយ JavaScript — WebView បើក UI ក្នុង
    /// Assets/www/index.html (offline, មិនត្រូវការ internet)។
    /// </summary>
    [Activity(Label = "@string/app_name", MainLauncher = true, Exported = true,
              Theme = "@android:style/Theme.DeviceDefault.Light.NoActionBar")]
    public class MainActivity : Activity
    {
        private WebView _web;

        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            var bar = Window;                       // status bar ច្បាស់លើ app ភ្លឺ
            bar.SetStatusBarColor(Color.ParseColor("#FFFFFF"));
            bar.SetNavigationBarColor(Color.ParseColor("#FFFFFF"));

            _web = new WebView(this);
            WebSettings settings = _web.Settings;
            settings.JavaScriptEnabled = true;
            settings.DomStorageEnabled = true;
            settings.AllowFileAccess = true;
            settings.SetSupportZoom(false);
            settings.BuiltInZoomControls = false;
            settings.MediaPlaybackRequiresUserGesture = false;

            _web.SetWebViewClient(new WebViewClient());
            _web.SetWebChromeClient(new WebChromeClient());
            _web.LoadUrl("file:///android_asset/www/index.html");

            SetContentView(_web);
        }

        public override void OnBackPressed()
        {
            if (_web != null && _web.CanGoBack())
            {
                _web.GoBack();
                return;
            }
            base.OnBackPressed();
        }
    }
}
