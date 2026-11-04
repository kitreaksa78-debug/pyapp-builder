using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Webkit;
using Android.Widget;

namespace PyApp
{
    /// <summary>
    /// App Android សរសេរដោយ C# (.NET for Android) — កែកូដនេះបានសេរី។
    /// </summary>
    [Activity(Label = "@string/app_name", MainLauncher = true, Exported = true)]
    public class MainActivity : Activity
    {
        // ដាក់ URL ដែល app នឹងបើកនៅទីនេះ (បើមិនប្រើ ទុកទទេ "")
        private const string HomeUrl = "https://lysagaming.com/";

        private WebView _web;

        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
            root.SetBackgroundColor(Color.White);

            _web = new WebView(this);
            WebSettings settings = _web.Settings;
            settings.JavaScriptEnabled = true;
            settings.DomStorageEnabled = true;

            _web.SetWebViewClient(new WebViewClient());
            _web.SetWebChromeClient(new WebChromeClient());
            _web.LoadUrl(HomeUrl);

            root.AddView(_web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MatchParent,
                ViewGroup.LayoutParams.MatchParent));

            SetContentView(root);
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
